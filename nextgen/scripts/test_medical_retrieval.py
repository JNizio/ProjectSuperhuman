#!/usr/bin/env python3
"""Contract tests for medical language distinctions and bounded lexical retrieval."""

from __future__ import annotations

import json
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
KNOWLEDGE = ROOT / "medical-knowledge"
NORMALIZE_RE = re.compile(r"[^a-z0-9]+")
BASELINE_IDS = {
    "gastroesophageal_reflux_disease", "irritable_bowel_syndrome", "coeliac_disease",
    "crohn_disease", "ulcerative_colitis", "asthma",
    "chronic_obstructive_pulmonary_disease", "community_acquired_pneumonia",
    "pulmonary_embolism", "hypertension", "atrial_fibrillation",
    "coronary_artery_disease", "heart_failure", "migraine",
    "benign_paroxysmal_positional_vertigo", "stroke_or_transient_ischemic_attack",
    "epilepsy", "type_2_diabetes_mellitus", "type_1_diabetes_mellitus",
    "hypothyroidism", "hyperthyroidism", "non_specific_low_back_pain",
    "lumbar_radiculopathy", "rheumatoid_arthritis", "osteoarthritis",
    "atopic_dermatitis", "psoriasis", "cellulitis", "urticaria", "influenza",
    "covid_19", "herpes_zoster", "infectious_mononucleosis", "allergic_rhinitis",
    "food_allergy", "anaphylaxis", "contact_dermatitis",
    "lower_urinary_tract_infection", "nephrolithiasis", "chronic_kidney_disease",
    "prostatitis", "generalized_anxiety_disorder", "panic_disorder",
    "major_depressive_disorder", "bipolar_disorder", "acute_sinusitis",
    "acute_otitis_media", "tinnitus", "acute_tonsillitis", "conjunctivitis",
    "dry_eye_disease", "acute_angle_closure_glaucoma", "age_related_cataract",
    "endometriosis", "menopause_transition", "testicular_torsion",
    "pelvic_inflammatory_disease", "iron_deficiency_anemia",
    "vitamin_b12_deficiency", "folate_deficiency", "vitamin_d_deficiency",
    "fibromyalgia", "tension_type_headache", "costochondritis",
}


def normalize(value: str) -> str:
    return " ".join(NORMALIZE_RE.sub(" ", value.lower()).split())


class MedicalRetrievalContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.corpus = json.loads((KNOWLEDGE / "conditions.v1.json").read_text(encoding="utf-8"))
        cls.symptoms = json.loads((KNOWLEDGE / "symptom-index.v1.json").read_text(encoding="utf-8"))
        cls.language = json.loads((KNOWLEDGE / "symptom-language.v1.json").read_text(encoding="utf-8"))
        cls.index = json.loads((KNOWLEDGE / "medical-lexical-index.v1.json").read_text(encoding="utf-8"))

    def candidates(self, question: str) -> set[str]:
        words = normalize(question).split()[:48]
        phrases = {
            " ".join(words[start:start + size])
            for start in range(len(words))
            for size in range(1, min(6, len(words) - start) + 1)
        }
        scores: dict[str, int] = {}

        def add(ids: list[str], weight: int) -> None:
            for condition_id in ids[:64]:
                scores[condition_id] = scores.get(condition_id, 0) + weight

        for phrase in phrases:
            add(self.index["condition_phrase_postings"].get(phrase, []), 12)
            for symptom_id in self.index["symptom_phrase_postings"].get(phrase, [])[:16]:
                add(self.index["symptom_condition_postings"].get(symptom_id, []), 6)
        for token in words:
            add(self.index["condition_token_postings"].get(token, []), 2)
            for symptom_id in self.index["symptom_token_postings"].get(token, [])[:16]:
                add(self.index["symptom_condition_postings"].get(symptom_id, []), 1)
        return {
            condition_id
            for condition_id, _ in sorted(scores.items(), key=lambda item: (-item[1], item[0]))[:128]
        }

    def test_all_baseline_condition_ids_are_preserved(self) -> None:
        ids = {condition["id"] for condition in self.corpus["conditions"]}
        self.assertTrue(BASELINE_IDS <= ids)
        self.assertEqual(188, len(ids))

    def test_lay_phrases_retrieve_bounded_educational_candidates(self) -> None:
        self.assertIn("irritable_bowel_syndrome", self.candidates("I've got a tummy ache"))
        self.assertIn("benign_paroxysmal_positional_vertigo", self.candidates("the room is spinning"))
        self.assertIn("supraventricular_tachycardia", self.candidates("my heart is racing"))
        self.assertIn("asthma", self.candidates("I can't catch my breath"))
        self.assertIn("acute_myocardial_infarction", self.candidates("heart attack"))

    def test_dizziness_terms_are_not_collapsed(self) -> None:
        language = {item["symptom_id"]: item for item in self.language["terms"]}
        dizziness = language["dizziness"]
        self.assertNotIn("lightheadedness", {item["term"] for item in dizziness["aliases"]})
        self.assertNotIn("vertigo", {item["term"] for item in dizziness["aliases"]})
        related = {item["term"]: item["relationship"] for item in dizziness["related_terms"]}
        self.assertEqual("related_not_equivalent", related["lightheadedness"])
        self.assertEqual("related_not_equivalent", related["vertigo"])

    def test_postings_and_runtime_candidate_pool_are_bounded(self) -> None:
        self.assertEqual(64, self.index["limits"]["max_posting_size"])
        self.assertEqual(128, self.index["limits"]["max_runtime_candidates"])
        for field in (
            "condition_phrase_postings", "symptom_phrase_postings",
            "condition_token_postings", "symptom_token_postings",
            "symptom_condition_postings",
        ):
            self.assertLessEqual(max(map(len, self.index[field].values())), 64)

    def test_aliases_are_candidates_not_probabilities(self) -> None:
        serialized = json.dumps(self.symptoms).lower()
        self.assertNotIn("probability", serialized)
        self.assertNotIn("definitely have", serialized)
        self.assertIn("not diagnoses", self.symptoms["disclaimer"])


if __name__ == "__main__":
    unittest.main()
