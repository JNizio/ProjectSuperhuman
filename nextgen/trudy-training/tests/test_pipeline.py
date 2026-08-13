import json, tempfile, unittest
from pathlib import Path
from unittest.mock import patch

from trudy_training.adversarial import adversarial_examples, ensure_excluded_from_training
from trudy_training.backends import HuggingFacePeftBackend, TrainingDependencyError
from trudy_training.evaluator import compare_reports
from trudy_training.formatting import build_loss_mask, contains_hidden_reasoning, parse_tool_call, render_supervised, serialize_tool_call
from trudy_training.generator import generate_synthetic
from trudy_training.packaging import build_manifest, load_manifest, write_manifest
from trudy_training.privacy import PrivacyViolation, assert_training_safe, validate_training_path
from trudy_training.split import deterministic_split
from trudy_training.training_config import FineTuneConfig

class FakeTokenizer:
    bos_token_id=1; eos_token_id=2
    def __call__(self,text,add_special_tokens=False,**kwargs): return {"input_ids":[10+(ord(c)%80) for c in text]}

class FineTunePipelineTest(unittest.TestCase):
    def test_config_validation(self):
        FineTuneConfig(base_model="model",output_dir="out",quantization="none",precision="fp32").validate()
        with self.assertRaises(ValueError): FineTuneConfig(base_model="",output_dir="out").validate()
        with self.assertRaises(ValueError): FineTuneConfig(base_model="m",output_dir="out",lora_rank=0).validate()

    def test_tool_serialization_round_trip(self):
        op={"name":"get_metric_history","domains":["SLEEP"],"arguments":{"domain":"SLEEP","metricId":"score"}}
        self.assertEqual(parse_tool_call(serialize_tool_call(op)),op)

    def test_train_eval_split_isolation(self):
        examples=generate_synthetic(9); train,val=deterministic_split(examples,.2)
        self.assertFalse({x["example_id"] for x in train}&{x["example_id"] for x in val})

    def test_adversarial_excluded_from_training(self):
        adv=adversarial_examples(); self.assertTrue(all("eval_only" in x["quality"]["tags"] for x in adv))
        with self.assertRaises(ValueError): ensure_excluded_from_training(adv)

    def test_loss_mask_only_trains_calls_and_answers(self):
        ex=generate_synthetic(3)[0]; sample=render_supervised(ex,"policy"); masked=build_loss_mask(FakeTokenizer(),sample,20000)
        self.assertEqual(len(masked["input_ids"]),len(masked["labels"]))
        self.assertIn(-100,masked["labels"]); self.assertTrue(any(x!=-100 for x in masked["labels"]))
        result_text=next(s.text for s in sample.segments if "<trudy_tool_result>" in s.text)
        self.assertTrue(any((not s.train) and s.text==result_text for s in sample.segments))

    def test_deterministic_seed_behavior(self):
        self.assertEqual(generate_synthetic(17),generate_synthetic(17)); self.assertNotEqual(generate_synthetic(17),generate_synthetic(18))
        self.assertGreaterEqual(len(generate_synthetic(17)),300)

    def test_manifest_validation_round_trip(self):
        manifest=build_manifest(package_version="1",base_model="base",adapter_path="adapter",tokenizer="tok",dataset_version="d",policy_version="p",tool_contract_version="t",training_config_hash="a"*64,eval_score=.91,supported_context_length=2048,quantization="4bit",license_name="test",license_source="local")
        with tempfile.TemporaryDirectory() as d:
            p=Path(d)/"manifest.json"; write_manifest(p,manifest); self.assertEqual(load_manifest(p),manifest)

    def test_evaluator_regression_comparison(self):
        base={"aggregate_score":.9,"score_by_category":{"stale_data":.9},"failure_reasons":{}}
        cand={"aggregate_score":.85,"score_by_category":{"stale_data":.7},"failure_reasons":{"x":["stale_data_not_acknowledged"]}}
        result=compare_reports(base,cand); self.assertFalse(result["promotion_pass"]); self.assertIn("stale_data",result["regressions"]); self.assertIn("x",result["new_failures"])

    def test_no_chain_of_thought(self):
        for ex in generate_synthetic(17)+adversarial_examples(): self.assertFalse(contains_hidden_reasoning(json.dumps(ex)))

    def test_no_sensitive_fields_or_files(self):
        assert_training_safe(generate_synthetic(17))
        with self.assertRaises(PrivacyViolation): assert_training_safe([{"device_id":"abc"}])
        with self.assertRaises(PrivacyViolation): validate_training_path("phone-health.db")

    def test_missing_optional_dependency_fails_cleanly(self):
        cfg=FineTuneConfig(base_model="m",output_dir="out",quantization="none",precision="fp32")
        real_import=__import__
        def blocked(name,*args,**kwargs):
            if name=="torch": raise ImportError("blocked")
            return real_import(name,*args,**kwargs)
        with patch("builtins.__import__",side_effect=blocked):
            with self.assertRaises(TrainingDependencyError): HuggingFacePeftBackend().train(cfg,[generate_synthetic(1)[0]],[],"policy")

if __name__=="__main__": unittest.main()
