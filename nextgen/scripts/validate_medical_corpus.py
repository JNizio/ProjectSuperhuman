#!/usr/bin/env python3
"""Validate Trudy's medical corpus and deterministically build bounded retrieval indexes."""

from __future__ import annotations

import argparse
import json
import re
from collections import Counter, defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
KNOWLEDGE = ROOT / "medical-knowledge"
CORPUS = KNOWLEDGE / "conditions.v1.json"
LANGUAGE = KNOWLEDGE / "symptom-language.v1.json"
SYMPTOM_INDEX = KNOWLEDGE / "symptom-index.v1.json"
LEXICAL_INDEX = KNOWLEDGE / "medical-lexical-index.v1.json"
ID_RE = re.compile(r"^[a-z][a-z0-9_]*$")
NON_ALPHANUMERIC = re.compile(r"[^a-z0-9]+")
SYSTEMS = {
    "gastrointestinal", "respiratory", "cardiovascular", "neurological",
    "endocrine_metabolic", "musculoskeletal", "dermatological", "infectious",
    "allergy_immunology", "urinary_renal", "mental_health", "ent", "eye",
    "reproductive", "haematological_nutritional", "pain", "sleep", "autonomic",
}
ALIAS_TYPES = {
    "lay_term", "clinical_term", "abbreviation", "british_spelling",
    "american_spelling", "common_misspelling", "alternative_name",
}
URGENCIES = {"emergency", "urgent_same_day", "prompt_clinical_review"}
FREQUENCIES = ("common", "possible", "uncommon")
FREQUENCY_RANK = {name: index for index, name in enumerate(FREQUENCIES)}
STOP_WORDS = {
    "have", "with", "that", "this", "from", "what", "could", "would", "about",
    "been", "does", "feel", "feeling", "when", "your", "some", "more", "very",
    "also", "like", "pain", "disease", "syndrome", "disorder", "acute", "chronic",
}
MAX_POSTING_SIZE = 64


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def string_list(values: object, field: str) -> list[str]:
    require(isinstance(values, list), f"{field} must be a list")
    require(all(isinstance(value, str) and value.strip() for value in values), f"{field} has blank/non-string value")
    require(len(values) == len(set(values)), f"{field} has duplicates")
    return values


def normalize(value: str) -> str:
    return " ".join(NON_ALPHANUMERIC.sub(" ", value.lower()).split())


def validate_language(language: dict) -> dict[str, dict]:
    require(language.get("schema_version") == "1.0.0", "unsupported symptom-language version")
    result: dict[str, dict] = {}
    for record in language.get("terms", []):
        symptom_id = record.get("symptom_id", "")
        require(ID_RE.fullmatch(symptom_id) is not None and symptom_id not in result, f"invalid/duplicate language symptom: {symptom_id}")
        aliases = record.get("aliases", [])
        terms = []
        for alias in aliases:
            require(alias.get("match_type") == "lexical_equivalent", f"{symptom_id} has unsafe alias match type")
            term = alias.get("term", "").strip()
            require(term and term not in terms, f"{symptom_id} has blank/duplicate alias")
            terms.append(term)
        related_terms = record.get("related_terms", [])
        for related in related_terms:
            require(related.get("relationship") == "related_not_equivalent", f"{symptom_id} related term must stay non-equivalent")
            require(related.get("term", "").strip() and related.get("note", "").strip(), f"{symptom_id} has incomplete related term")
            require(related["term"] not in terms, f"{symptom_id} promotes a related term to an alias")
        result[symptom_id] = {"aliases": terms, "related_terms": related_terms}
    return result


def validate_corpus(data: dict) -> tuple[dict[str, list[dict]], dict[str, Counter[str]]]:
    require(data.get("schema_version") == "1.1.0", "unsupported corpus schema version")
    require(data.get("corpus_version") == "2.0.0", "unexpected corpus version")
    require("not diagnoses" in data.get("disclaimer", ""), "non-diagnostic disclaimer missing")
    require("British" in data.get("terminology_policy", "") and "American" in data.get("terminology_policy", ""), "terminology policy missing")

    sources = data.get("sources", [])
    require(sources, "source catalogue is empty")
    source_ids: set[str] = set()
    for source in sources:
        source_id = source.get("source_id", "")
        require(ID_RE.fullmatch(source_id) is not None and source_id not in source_ids, f"duplicate/invalid source ID: {source_id}")
        source_ids.add(source_id)
        require(source.get("organisation") and source.get("title"), f"source {source_id} missing attribution")
        require(source.get("url", "").startswith("https://"), f"source {source_id} must use HTTPS")
        string_list(source.get("evidence_scope"), f"source {source_id}.evidence_scope")
        require(re.fullmatch(r"\d{4}-\d{2}-\d{2}", source.get("accessed_on", "")) is not None, f"source {source_id} has invalid date")

    conditions = data.get("conditions", [])
    require(len(conditions) >= 180, "v2 broad-coverage floor is 180 conditions")
    condition_ids: set[str] = set()
    red_ids: set[str] = set()
    system_counts: Counter[str] = Counter()
    links: dict[str, list[dict]] = defaultdict(list)
    labels: dict[str, Counter[str]] = defaultdict(Counter)
    required = {
        "id", "display_name", "aliases", "alias_records", "body_systems", "categories",
        "description", "features", "typical_onset_and_course", "risk_factors",
        "common_associations", "differentiating_features", "red_flags",
        "common_investigations", "diagnostic_context", "severity_and_emergency_notes",
        "population_context", "source_refs", "review",
    }

    for condition in conditions:
        condition_id = condition.get("id", "")
        require(ID_RE.fullmatch(condition_id) is not None and condition_id not in condition_ids, f"duplicate/invalid condition ID: {condition_id}")
        condition_ids.add(condition_id)
        require(required <= condition.keys(), f"{condition_id} missing fields: {sorted(required - condition.keys())}")
        require(condition["display_name"].strip() and condition["description"].strip(), f"{condition_id} missing display text")
        aliases = string_list(condition["aliases"], f"{condition_id}.aliases")
        alias_records = condition["alias_records"]
        require([item.get("term") for item in alias_records] == aliases, f"{condition_id}.alias_records must mirror aliases")
        require(all(item.get("alias_type") in ALIAS_TYPES for item in alias_records), f"{condition_id} has unknown alias type")
        systems = string_list(condition["body_systems"], f"{condition_id}.body_systems")
        require(set(systems) <= SYSTEMS, f"{condition_id} has unknown body system")
        system_counts.update(systems)
        for field in ("categories", "risk_factors", "common_associations", "population_context"):
            string_list(condition[field], f"{condition_id}.{field}")
        for field in ("typical_onset_and_course", "diagnostic_context", "severity_and_emergency_notes"):
            require(condition[field].strip(), f"{condition_id} missing {field}")

        features = condition["features"]
        require(set(features) == set(FREQUENCIES) and features["common"], f"{condition_id} has invalid feature groups")
        seen_features: set[str] = set()
        for frequency in FREQUENCIES:
            for feature in features[frequency]:
                require(isinstance(feature, list) and len(feature) in (2, 3), f"{condition_id} malformed feature")
                symptom_id, label = feature[:2]
                require(ID_RE.fullmatch(symptom_id) is not None and label.strip() and symptom_id not in seen_features, f"{condition_id} invalid/repeated feature {symptom_id}")
                seen_features.add(symptom_id)
                context = feature[2] if len(feature) == 3 else None
                if context is not None:
                    require(context.strip(), f"{condition_id}/{symptom_id} has blank context")
                link = {"condition_id": condition_id, "frequency": frequency}
                if context:
                    link["context"] = context
                links[symptom_id].append(link)
                labels[symptom_id][label] += 1

        for item in condition["differentiating_features"]:
            require(isinstance(item, list) and len(item) == 2 and item[0] in {"characteristic", "important_negative", "alternative_context"} and item[1].strip(), f"{condition_id} malformed differentiator")
        for red_flag in condition["red_flags"]:
            require(isinstance(red_flag, list) and len(red_flag) == 4, f"{condition_id} malformed red flag")
            red_id, urgency, description, context = red_flag
            require(ID_RE.fullmatch(red_id) is not None and red_id not in red_ids, f"duplicate/invalid red flag {red_id}")
            red_ids.add(red_id)
            require(urgency in URGENCIES and description.strip() and context.strip(), f"{condition_id} invalid red flag")
        for investigation in condition["common_investigations"]:
            require(isinstance(investigation, list) and len(investigation) in (2, 3) and all(isinstance(value, str) and value.strip() for value in investigation), f"{condition_id} malformed investigation")
        refs = string_list(condition["source_refs"], f"{condition_id}.source_refs")
        require(set(refs) <= source_ids, f"{condition_id} references unknown source")
        review = condition["review"]
        require(review.get("corpus_version") == data["corpus_version"] and review.get("last_reviewed_on") and review.get("review_status") in {"curated", "needs_clinical_review"}, f"{condition_id} invalid review metadata")
        certainty_text = " ".join(str(value) for value in condition.values()).lower()
        for forbidden in ("symptom means you have", "proves you have", "definitely have"):
            require(forbidden not in certainty_text, f"{condition_id} contains forbidden certainty wording")

    require(set(system_counts) == SYSTEMS, f"missing systems: {sorted(SYSTEMS - set(system_counts))}")
    require(len(links) >= 500, "v2 symptom coverage floor is 500")
    require(len(red_ids) >= 100, "v2 red-flag coverage floor is 100")
    return links, labels


def build_symptom_index(data: dict, language: dict[str, dict], links: dict[str, list[dict]], labels: dict[str, Counter[str]]) -> dict:
    symptoms = []
    for symptom_id in sorted(links):
        display_name = sorted(labels[symptom_id].items(), key=lambda item: (-item[1], item[0]))[0][0]
        language_record = language.get(symptom_id, {"aliases": [], "related_terms": []})
        aliases = [term for term in language_record["aliases"] if term.lower() != display_name.lower()]
        symptoms.append({
            "symptom_id": symptom_id,
            "display_name": display_name,
            "aliases": aliases,
            "alias_records": [{"term": term, "match_type": "lexical_equivalent"} for term in aliases],
            "related_terms": language_record["related_terms"],
            "condition_links": sorted(links[symptom_id], key=lambda link: (FREQUENCY_RANK[link["frequency"]], link["condition_id"])),
        })
    return {
        "schema_version": "1.1.0",
        "corpus_version": data["corpus_version"],
        "generated_from": "conditions.v1.json",
        "language_source": "symptom-language.v1.json",
        "disclaimer": data["disclaimer"],
        "terminology_note": "Aliases retrieve lexical candidates. related_terms explicitly record phrases that must not be treated as medically identical.",
        "symptoms": symptoms,
    }


def build_lexical_index(data: dict, symptom_index: dict) -> dict:
    condition_phrases: dict[str, set[str]] = defaultdict(set)
    symptom_phrases: dict[str, set[str]] = defaultdict(set)
    condition_tokens: dict[str, set[str]] = defaultdict(set)
    symptom_tokens: dict[str, set[str]] = defaultdict(set)

    def add_terms(terms: list[str], item_id: str, phrases: dict[str, set[str]], tokens: dict[str, set[str]]) -> None:
        for term in terms:
            normalized = normalize(term)
            if not normalized:
                continue
            phrases[normalized].add(item_id)
            for token in normalized.split():
                if len(token) >= 3 and token not in STOP_WORDS:
                    tokens[token].add(item_id)

    for condition in data["conditions"]:
        add_terms(
            [condition["display_name"], condition["id"].replace("_", " "), *condition["aliases"]],
            condition["id"], condition_phrases, condition_tokens,
        )
    for symptom in symptom_index["symptoms"]:
        add_terms(
            [symptom["display_name"], symptom["symptom_id"].replace("_", " "), *symptom["aliases"]],
            symptom["symptom_id"], symptom_phrases, symptom_tokens,
        )

    def freeze(postings: dict[str, set[str]]) -> dict[str, list[str]]:
        return {term: sorted(ids)[:MAX_POSTING_SIZE] for term, ids in sorted(postings.items())}

    return {
        "schema_version": "1.0.0",
        "corpus_version": data["corpus_version"],
        "generated_from": ["conditions.v1.json", "symptom-index.v1.json"],
        "normalization": "lowercase_ascii_alphanumeric_space_v1",
        "limits": {"max_ngram_words": 6, "max_posting_size": 64, "max_runtime_candidates": 128},
        "condition_phrase_postings": freeze(condition_phrases),
        "symptom_phrase_postings": freeze(symptom_phrases),
        "condition_token_postings": freeze(condition_tokens),
        "symptom_token_postings": freeze(symptom_tokens),
        "symptom_condition_postings": {
            symptom["symptom_id"]: sorted({link["condition_id"] for link in symptom["condition_links"]})
            for symptom in symptom_index["symptoms"]
        },
        "stats": {
            "condition_count": len(data["conditions"]),
            "symptom_count": len(symptom_index["symptoms"]),
            "condition_phrase_count": len(condition_phrases),
            "symptom_phrase_count": len(symptom_phrases),
        },
    }


def render(value: dict) -> str:
    return json.dumps(value, indent=2, ensure_ascii=False) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true", help="fail if either generated index is stale")
    args = parser.parse_args()
    data = json.loads(CORPUS.read_text(encoding="utf-8"))
    language = validate_language(json.loads(LANGUAGE.read_text(encoding="utf-8")))
    links, labels = validate_corpus(data)
    symptom_index = build_symptom_index(data, language, links, labels)
    lexical_index = build_lexical_index(data, symptom_index)
    symptom_rendered, lexical_rendered = render(symptom_index), render(lexical_index)

    if args.check:
        require(SYMPTOM_INDEX.exists() and SYMPTOM_INDEX.read_text(encoding="utf-8") == symptom_rendered, "symptom index is stale")
        require(LEXICAL_INDEX.exists() and LEXICAL_INDEX.read_text(encoding="utf-8") == lexical_rendered, "lexical index is stale")
    else:
        SYMPTOM_INDEX.write_text(symptom_rendered, encoding="utf-8")
        LEXICAL_INDEX.write_text(lexical_rendered, encoding="utf-8")

    condition_aliases = sum(len(condition["aliases"]) for condition in data["conditions"])
    symptom_aliases = sum(len(symptom["aliases"]) for symptom in symptom_index["symptoms"])
    print(
        f"validated {len(data['conditions'])} conditions, {len(symptom_index['symptoms'])} symptoms, "
        f"{condition_aliases + symptom_aliases} aliases, {len(data['sources'])} sources; "
        f"indexed {lexical_index['stats']['condition_phrase_count']} condition and "
        f"{lexical_index['stats']['symptom_phrase_count']} symptom phrases"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
