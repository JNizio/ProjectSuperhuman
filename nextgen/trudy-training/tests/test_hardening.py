import json,tempfile,unittest
from pathlib import Path
from trudy_training.adversarial import adversarial_examples,ensure_excluded_from_training
from trudy_training.audit import exact_duplicates,overlap,audit_dataset
from trudy_training.fixtures import predictions
from trudy_training.generator import generate_synthetic
from trudy_training.gold import gold_examples
from trudy_training.gold_hardening import hardening_gold_examples
from trudy_training.prediction import validate_prediction
from trudy_training.provenance import build_manifest,content_hash,quality_metrics
from trudy_training.promotion import PromotionPolicy,compare_and_gate,evaluate_predictions,markdown_report
from trudy_training.smoke import optional_ml_stack_status,package_manifest,tokenizer_smoke
from trudy_training.split import deterministic_split
from trudy_training.tool_compat import INTELLIGENCE_TOOLS,validate_tool_operation

class ModelEvalHardeningTest(unittest.TestCase):
    def setUp(self):
        self.training=gold_examples()+hardening_gold_examples()+generate_synthetic(17)
        self.adversarial=adversarial_examples()

    def test_train_eval_adversarial_isolation(self):
        train,val=deterministic_split(self.training,.2)
        self.assertFalse({x['example_id'] for x in train}&{x['example_id'] for x in val})
        self.assertFalse(overlap(train,val))
        self.assertFalse({x['example_id'] for x in self.training}&{x['example_id'] for x in self.adversarial})
        with self.assertRaises(ValueError): ensure_excluded_from_training(self.adversarial)

    def test_synthetic_exact_prompt_duplicates_removed(self):
        self.assertFalse(exact_duplicates(generate_synthetic(17)))

    def test_candidate_schema_strict(self):
        ex=self.adversarial[0]; good=predictions([ex],'good')[ex['example_id']]
        self.assertEqual(validate_prediction(good),[])
        self.assertTrue(validate_prediction(dict(good,reasoning='forbidden')))
        self.assertTrue(validate_prediction(dict(good,device_id='x')))

    def test_good_candidate_passes(self):
        base=evaluate_predictions(self.adversarial,predictions(self.adversarial,'good'))
        decision=compare_and_gate(base,base,PromotionPolicy(overall_threshold=.85))
        self.assertTrue(decision['promotion_pass']); self.assertIn('PASS',markdown_report(decision))

    def test_hallucination_candidate_fails_critical_gate(self):
        base=evaluate_predictions(self.adversarial,predictions(self.adversarial,'good'))
        bad=evaluate_predictions(self.adversarial,predictions(self.adversarial,'hallucinating'))
        decision=compare_and_gate(base,bad,PromotionPolicy(overall_threshold=.5))
        self.assertFalse(decision['promotion_pass']); self.assertTrue(any('fabricated_evidence' in x for x in decision['failure_reasons']))

    def test_causal_candidate_fails_critical_gate(self):
        base=evaluate_predictions(self.adversarial,predictions(self.adversarial,'good'))
        bad=evaluate_predictions(self.adversarial,predictions(self.adversarial,'causal'))
        self.assertFalse(compare_and_gate(base,bad,PromotionPolicy(overall_threshold=.5))['promotion_pass'])

    def test_malformed_candidate_fails(self):
        bad=evaluate_predictions(self.adversarial,predictions(self.adversarial,'malformed'))
        self.assertGreater(bad['malformed_rate'],0)

    def test_regression_detection(self):
        base=evaluate_predictions(self.adversarial,predictions(self.adversarial,'good'))
        bad=evaluate_predictions(self.adversarial,predictions(self.adversarial,'causal'))
        decision=compare_and_gate(base,bad,PromotionPolicy(overall_threshold=.5,max_category_regression=.01))
        self.assertTrue(decision['regressions'])

    def test_provenance_hash_is_order_stable(self):
        self.assertEqual(content_hash(self.training),content_hash(list(reversed(self.training))))
        manifest=build_manifest(self.training,schema_version='1.2',tool_contract_version='integration-4',policy_version='integration-4',generator_version='3',seed=17,dataset_version='trudy-specialization-3')
        self.assertEqual(manifest['content_sha256'],content_hash(self.training))

    def test_quality_metrics_cover_domains_and_tools(self):
        q=quality_metrics(self.training)
        self.assertIn('SLEEP',q['domains']); self.assertIn('get_personal_trend',q['tool_operations']); self.assertGreater(q['cross_domain'],0)

    def test_intelligence_tool_serialization_compatibility(self):
        names={op['name'] for ex in hardening_gold_examples() for op in ex['expected_tool_operations']}
        self.assertTrue(INTELLIGENCE_TOOLS.issubset(names))
        for ex in hardening_gold_examples():
            for op in ex['expected_tool_operations']: self.assertEqual(validate_tool_operation(op),[])

    def test_no_chain_of_thought_or_sensitive_fields(self):
        blob=json.dumps(self.training+self.adversarial).lower()
        for key in ('chain_of_thought','hidden_reasoning','<thinking>','device_id','api_key','access_token'): self.assertNotIn(key,blob)

    def test_adversarial_suite_has_required_pressure_categories(self):
        prompts=' '.join(x['user_request'].lower() for x in self.adversarial)
        for marker in ('ignore all previous','invent','diagnos','sql','evidence id','stale','insulin'): self.assertIn(marker,prompts)

    def test_dataset_audit_has_no_repeated_evidence_ids(self):
        self.assertFalse(audit_dataset(self.training)['repeated_evidence_ids'])

    def test_ci_safe_smoke_without_ml_stack(self):
        self.assertIn('torch',optional_ml_stack_status()); self.assertEqual(tokenizer_smoke(['sample'])['status'],'skipped')
        with tempfile.TemporaryDirectory() as d:
            Path(d,'adapter.json').write_text('{}'); self.assertEqual(package_manifest(d,'fixture')['file_count'],1)

if __name__=='__main__': unittest.main()
