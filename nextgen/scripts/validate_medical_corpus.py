#!/usr/bin/env python3
"""Validate the Trudy medical corpus and deterministically build its reverse index."""

from __future__ import annotations

import argparse
import json
import re
from collections import Counter, defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CORPUS = ROOT / "medical-knowledge" / "conditions.v1.json"
INDEX = ROOT / "medical-knowledge" / "symptom-index.v1.json"
ID_RE = re.compile(r"^[a-z][a-z0-9_]*$")
SYSTEMS = {"gastrointestinal", "respiratory", "cardiovascular", "neurological", "endocrine_metabolic", "musculoskeletal", "dermatological", "infectious", "allergy_immunology", "urinary_renal", "mental_health", "ent", "eye", "reproductive", "haematological_nutritional", "pain"}
URGENCIES = {"emergency", "urgent_same_day", "prompt_clinical_review"}
FREQUENCIES = ("common", "possible", "uncommon")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def string_list(values: object, field: str) -> list[str]:
    require(isinstance(values, list), f"{field} must be a list")
    require(all(isinstance(v, str) and v.strip() for v in values), f"{field} has blank/non-string value")
    require(len(values) == len(set(values)), f"{field} has duplicates")
    return values


def validate(data: dict) -> dict:
    require(data.get("schema_version") == "1.0.0", "unsupported schema version")
    require("not diagnoses" in data.get("disclaimer", ""), "non-diagnostic disclaimer missing")
    sources = data.get("sources", [])
    require(sources, "source catalogue is empty")
    source_ids: set[str] = set()
    for source in sources:
        sid = source.get("source_id", "")
        require(sid and sid not in source_ids, f"duplicate/blank source ID: {sid}")
        source_ids.add(sid)
        require(source.get("organisation") and source.get("title"), f"source {sid} missing attribution")
        require(source.get("url", "").startswith("https://"), f"source {sid} must use HTTPS")
        string_list(source.get("evidence_scope"), f"source {sid} evidence_scope")
        require(re.fullmatch(r"\d{4}-\d{2}-\d{2}", source.get("accessed_on", "")) is not None, f"source {sid} has invalid access date")

    conditions = data.get("conditions", [])
    require(len(conditions) >= 50, "broad-coverage floor is 50 conditions")
    condition_ids: set[str] = set()
    red_ids: set[str] = set()
    system_counts: Counter[str] = Counter()
    index: dict[str, list[dict]] = defaultdict(list)
    labels: dict[str, Counter[str]] = defaultdict(Counter)
    required = {"id", "display_name", "aliases", "body_systems", "categories", "description", "features", "typical_onset_and_course", "risk_factors", "common_associations", "differentiating_features", "red_flags", "common_investigations", "diagnostic_context", "severity_and_emergency_notes", "population_context", "source_refs", "review"}

    for condition in conditions:
        cid = condition.get("id", "")
        require(ID_RE.fullmatch(cid) is not None and cid not in condition_ids, f"duplicate/invalid condition ID: {cid}")
        condition_ids.add(cid)
        require(required <= condition.keys(), f"{cid} missing fields: {sorted(required - condition.keys())}")
        require(condition["display_name"].strip() and condition["description"].strip(), f"{cid} missing display text")
        string_list(condition["aliases"], f"{cid}.aliases")
        systems = string_list(condition["body_systems"], f"{cid}.body_systems")
        require(set(systems) <= SYSTEMS, f"{cid} has unknown body system")
        system_counts.update(systems)
        for field in ("categories", "risk_factors", "common_associations", "population_context"):
            string_list(condition[field], f"{cid}.{field}")
        for field in ("typical_onset_and_course", "diagnostic_context", "severity_and_emergency_notes"):
            require(condition[field].strip(), f"{cid} missing {field}")

        features = condition["features"]
        require(set(features) == set(FREQUENCIES) and features["common"], f"{cid} has invalid feature groups")
        seen: set[str] = set()
        for frequency in FREQUENCIES:
            for feature in features[frequency]:
                require(isinstance(feature, list) and len(feature) in (2, 3), f"{cid} malformed feature")
                symptom_id, label = feature[:2]
                require(ID_RE.fullmatch(symptom_id) is not None and label.strip() and symptom_id not in seen, f"{cid} invalid/repeated feature {symptom_id}")
                seen.add(symptom_id)
                context = feature[2] if len(feature) == 3 else None
                if context is not None:
                    require(context.strip(), f"{cid}/{symptom_id} has blank context")
                index[symptom_id].append({"condition_id": cid, "frequency": frequency, **({"context": context} if context else {})})
                labels[symptom_id][label] += 1

        for item in condition["differentiating_features"]:
            require(isinstance(item, list) and len(item) == 2 and item[0] in {"characteristic", "important_negative", "alternative_context"} and item[1].strip(), f"{cid} malformed differentiator")
        for red in condition["red_flags"]:
            require(isinstance(red, list) and len(red) == 4, f"{cid} malformed red flag")
            rid, urgency, description, context = red
            require(ID_RE.fullmatch(rid) is not None and rid not in red_ids, f"duplicate/invalid red flag {rid}")
            red_ids.add(rid)
            require(urgency in URGENCIES and description.strip() and context.strip(), f"{cid} invalid red flag")
        for investigation in condition["common_investigations"]:
            require(isinstance(investigation, list) and len(investigation) in (2, 3) and all(isinstance(v, str) and v.strip() for v in investigation), f"{cid} malformed investigation")
        refs = string_list(condition["source_refs"], f"{cid}.source_refs")
        require(set(refs) <= source_ids, f"{cid} references unknown source")
        review = condition["review"]
        require(review.get("corpus_version") == data["corpus_version"] and review.get("last_reviewed_on") and review.get("review_status") in {"curated", "needs_clinical_review"}, f"{cid} invalid review metadata")
        certainty_text = " ".join(str(v) for v in condition.values()).lower()
        for forbidden in ("symptom means you have", "proves you have", "definitely have"):
            require(forbidden not in certainty_text, f"{cid} contains forbidden certainty wording")

    require(set(system_counts) == SYSTEMS, f"missing systems: {sorted(SYSTEMS - set(system_counts))}")
    require(len(index) >= 80, "symptom coverage floor is 80 canonical symptoms")
    require(len(red_ids) >= 25, "red-flag coverage floor is 25 contextual flags")
    aliases = {
        "abdominal_pain":["stomach ache","tummy pain","belly pain"], "shortness_of_breath":["breathlessness","difficulty breathing","dyspnea"],
        "chest_pain":["chest discomfort"], "headache":["head pain"], "palpitations":["heart racing","pounding heartbeat"],
        "dizziness":["lightheadedness"], "diarrhea":["diarrhoea","loose stools"], "constipation":["difficulty passing stool"],
        "fatigue":["tiredness","low energy"], "nausea":["feeling sick"], "vomiting":["being sick"], "fever":["high temperature"],
        "rash":["skin rash"], "back_pain":["backache","lower back pain"], "numbness":["loss of sensation"],
        "tingling":["pins and needles","paresthesia"], "syncope":["fainting","blackout"],
    }
    entries = [{"symptom_id": sid, "display_name": labels[sid].most_common(1)[0][0], "aliases": aliases.get(sid, []), "condition_links": sorted(index[sid], key=lambda link: (link["frequency"], link["condition_id"]))} for sid in sorted(index)]
    return {"schema_version":"1.0.0", "corpus_version":data["corpus_version"], "generated_from":"conditions.v1.json", "disclaimer":data["disclaimer"], "symptoms":entries}


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true", help="fail if the generated index is stale")
    args = parser.parse_args()
    data = json.loads(CORPUS.read_text(encoding="utf-8"))
    generated = validate(data)
    rendered = json.dumps(generated, indent=2, ensure_ascii=False) + "\n"
    if args.check:
        require(INDEX.exists() and INDEX.read_text(encoding="utf-8") == rendered, "symptom index missing/stale; run without --check")
    else:
        INDEX.write_text(rendered, encoding="utf-8")
    print(f"validated {len(data['conditions'])} conditions, {len(generated['symptoms'])} symptoms, {len(data['sources'])} sources")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
