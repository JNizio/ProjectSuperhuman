import json, tempfile
from pathlib import Path
import unittest
from trudy_training.exporters import export_lines
from trudy_training.generator import generate_synthetic
from trudy_training.gold import gold_examples
from trudy_training.evaluator import evaluate
from trudy_training.io import read_jsonl, write_jsonl
from trudy_training.schema import validate_example_dict
from trudy_training.split import deterministic_split

class TrainingFoundationTest(unittest.TestCase):
    def setUp(self): self.examples=gold_examples()+generate_synthetic()
    def test_jsonl_round_trip(self):
        with tempfile.TemporaryDirectory() as d:
            p=Path(d)/"x.jsonl"; write_jsonl(p,self.examples); self.assertEqual(read_jsonl(p),self.examples)
    def test_schema_validation(self): self.assertEqual(validate_example_dict(self.examples[0]),[])
    def test_invalid_evidence_reference_rejected(self):
        x=json.loads(json.dumps(self.examples[0])); x["expected_evidence_references"][0]["evidence_id"]="missing"; self.assertTrue(any("invalid evidence" in e for e in validate_example_dict(x)))
    def test_domain_leakage_detected(self):
        r=evaluate(self.examples[0],{"answer":"ok","tool_operations":[{"name":"get_domain_state","domains":["BODY"]}],"evidence_references":[]}); self.assertIn("unrequested_domain_leakage",r.failures)
    def test_hallucinated_evidence_detected(self):
        ex=self.examples[0]; r=evaluate(ex,{"answer":"ok","tool_operations":ex["expected_tool_operations"],"evidence_references":["fake"]}); self.assertIn("hallucinated_evidence",r.failures)
    def test_causation_violation_detected(self):
        ex=next(e for e in self.examples if e["task_category"]=="association_not_causation")
        r=evaluate(ex,{"answer":"Exercise caused better sleep.","tool_operations":ex["expected_tool_operations"],"evidence_references":[]}); self.assertIn("causation_language_violation",r.failures)
    def test_stale_warning_check(self):
        ex=next(e for e in self.examples if e["task_category"]=="stale_data")
        r=evaluate(ex,{"answer":"Weight is 80 kg.","tool_operations":ex["expected_tool_operations"],"evidence_references":[]}); self.assertIn("stale_data_not_acknowledged",r.failures)
    def test_tool_failure_fallback_check(self):
        ex=next(e for e in self.examples if e["task_category"]=="tool_failure")
        r=evaluate(ex,{"answer":"Your resting heart rate is 60 bpm.","tool_operations":ex["expected_tool_operations"],"evidence_references":[]}); self.assertIn("tool_failure_fallback_incorrect",r.failures)
    def test_deterministic_synthetic(self): self.assertEqual(generate_synthetic(7),generate_synthetic(7))
    def test_deterministic_split(self): self.assertEqual(deterministic_split(self.examples),deterministic_split(list(reversed(self.examples))))
    def test_export_adapters(self):
        for fmt in ("instruction","chat","tool-use"):
            lines=export_lines(self.examples[:2],fmt).splitlines(); self.assertEqual(len(lines),2); json.loads(lines[0])
    def test_no_chain_of_thought_field(self):
        bad=json.loads(json.dumps(self.examples[0])); bad["chain_of_thought"]="secret"; self.assertTrue(any("chain-of-thought" in e for e in validate_example_dict(bad)))
    def test_no_pii_device_identifiers(self):
        serialized=json.dumps(self.examples).lower()
        for key in ("user_id","device_id","android_id","api_key","database_path"):
            self.assertNotIn(f'"{key}"',serialized)

if __name__=="__main__": unittest.main()
