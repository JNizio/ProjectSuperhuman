from __future__ import annotations
import json
from dataclasses import asdict
from .schema import EvidenceReference, QualityMetadata, ToolOperation, ToolResult, TrainingExample, VersionMetadata


def _ex(i, prompt, category, domains, answer, ops=(), evidence=(), refs=(), warnings=(), uncertainty=()):
    return json.loads(json.dumps(asdict(TrainingExample(
        example_id=f"gold-{i:02d}", version=VersionMetadata(), user_request=prompt, task_category=category,
        domains_involved=domains, expected_answer=answer, expected_tool_operations=ops, tool_results=evidence,
        expected_evidence_references=refs, warnings=warnings, uncertainty=uncertainty,
        quality=QualityMetadata("curated", True, ("gold",)),
    ))))


def gold_examples() -> list[dict]:
    sleep_ev={"evidence_id":"g01-sleep","domain":"SLEEP","metric_id":"sleep_score","value":78,"unit":"score","quality":"good"}
    weight_a={"evidence_id":"g02-w1","domain":"BODY","metric_id":"body_weight_kg","value":80.2,"unit":"kg","timestamp":"2026-07-01"}
    weight_b={"evidence_id":"g02-w2","domain":"BODY","metric_id":"body_weight_kg","value":79.1,"unit":"kg","timestamp":"2026-08-01"}
    tired_sleep={"evidence_id":"g03-sleep","domain":"SLEEP","metric_id":"sleep_total_minutes","value":330,"unit":"min"}
    tired_hyd={"evidence_id":"g03-water","domain":"HYDRATION","metric_id":"water_intake_ml","value":1200,"unit":"ml"}
    ex_sleep={"evidence_id":"g04-sleep","domain":"SLEEP","metric_id":"sleep_score","value":84}
    ex_work={"evidence_id":"g04-ex","domain":"EXERCISE","metric_id":"workout_sessions","value":1}
    today={"evidence_id":"g05-quality","domain":"SLEEP","metric_id":"sleep_score","value":64,"quality":"sparse"}
    stale={"evidence_id":"g07-stale","domain":"BODY","metric_id":"body_weight_kg","value":80.0,"quality":"stale"}
    multi1={"evidence_id":"g10-sleep","domain":"SLEEP","metric_id":"sleep_score","value":74}
    multi2={"evidence_id":"g10-mind","domain":"MINDFULNESS","metric_id":"mindfulness_minutes","value":20}
    return [
      _ex(1,"How was my sleep?","current_state",("SLEEP",),"Your supplied sleep score is 78. That is the available observation; no broader conclusion is supported by this example.",(ToolOperation("get_domain_state",("SLEEP",),{"domain":"SLEEP"}),),(ToolResult("get_domain_state",("SLEEP",),(sleep_ev,)),),(EvidenceReference("g01-sleep","SLEEP","sleep_score"),)),
      _ex(2,"Has my weight changed?","metric_history",("BODY",),"The supplied measurements decreased from 80.2 kg to 79.1 kg across the shown dates, a change of 1.1 kg. This describes the recorded trend only.",(ToolOperation("get_metric_history",("BODY",),{"domain":"BODY","metricId":"body_weight_kg"}),),(ToolResult("get_metric_history",("BODY",),(weight_a,weight_b)),),(EvidenceReference("g02-w1","BODY","body_weight_kg"),EvidenceReference("g02-w2","BODY","body_weight_kg"))),
      _ex(3,"Why am I tired?","cross_domain",("SLEEP","HYDRATION"),"The supplied data show short sleep and low recorded hydration. Either could be relevant, but these observations do not establish the cause of tiredness and other causes are possible.",(ToolOperation("get_context",("SLEEP","HYDRATION"),{"domains":["SLEEP","HYDRATION"]}),),(ToolResult("get_context",("SLEEP","HYDRATION"),(tired_sleep,tired_hyd)),),(EvidenceReference("g03-sleep","SLEEP","sleep_total_minutes"),EvidenceReference("g03-water","HYDRATION","water_intake_ml")),uncertainty=("Association only; no diagnosis or causal conclusion.",)),
      _ex(4,"Did exercise affect sleep?","association_not_causation",("EXERCISE","SLEEP"),"The supplied workout and sleep observations occurred in the same period, but this example cannot establish that exercise caused the sleep result.",(ToolOperation("get_context",("EXERCISE","SLEEP"),{"domains":["EXERCISE","SLEEP"]}),),(ToolResult("get_context",("EXERCISE","SLEEP"),(ex_sleep,ex_work)),),(EvidenceReference("g04-sleep","SLEEP","sleep_score"),EvidenceReference("g04-ex","EXERCISE","workout_sessions")),uncertainty=("Correlation is not causation.",)),
      _ex(5,"What should I pay attention to today?","insufficient_data",("SLEEP",),"The only supplied signal is a sparse sleep-score observation, so there is not enough evidence to rank broader health priorities today. Review the sleep signal, but avoid extrapolating beyond it.",(ToolOperation("get_data_quality",("SLEEP",),{"domain":"SLEEP"}),),(ToolResult("get_data_quality",("SLEEP",),(today,)),),(EvidenceReference("g05-quality","SLEEP","sleep_score"),),warnings=("Based on limited data.",)),
      _ex(6,"How is my nutrition today?","insufficient_data",("NUTRITION",),"No nutrition evidence was supplied, so I cannot assess today's nutrition from this example.",(ToolOperation("get_domain_state",("NUTRITION",),{"domain":"NUTRITION"}),),(ToolResult("get_domain_state",("NUTRITION",),()),),(),warnings=("No personal data available for this domain.",)),
      _ex(7,"Has my weight changed recently?","stale_data",("BODY",),"A body-weight value is supplied, but it is marked stale, so it is not sufficient to describe a recent change.",(ToolOperation("get_metric_history",("BODY",),{"domain":"BODY","metricId":"body_weight_kg"}),),(ToolResult("get_metric_history",("BODY",),(stale,)),),(EvidenceReference("g07-stale","BODY","body_weight_kg"),),warnings=("Data is stale.",)),
      _ex(8,"Which medication should I start?","general_health_context",(),"I cannot choose or prescribe a medication from the supplied dataset. This question needs appropriate clinical context and professional medical guidance.",(),(),(),warnings=("Unsupported for personalized medication selection.",)),
      _ex(9,"Did sleeping more cause my weight to fall?","association_not_causation",("SLEEP","BODY"),"Even if sleep and weight changed together, the supplied evidence does not establish that one caused the other. A causal conclusion would require stronger evidence.",(),(),(),uncertainty=("Association is not causation.",)),
      _ex(10,"Compare sleep and mindfulness.","cross_domain",("SLEEP","MINDFULNESS"),"The supplied sleep score is 74 and mindfulness duration is 20 minutes. They are different measures; report each directly rather than treating them as interchangeable.",(ToolOperation("get_context",("SLEEP","MINDFULNESS"),{"domains":["SLEEP","MINDFULNESS"]}),),(ToolResult("get_context",("SLEEP","MINDFULNESS"),(multi1,multi2)),),(EvidenceReference("g10-sleep","SLEEP","sleep_score"),EvidenceReference("g10-mind","MINDFULNESS","mindfulness_minutes"))),
      _ex(11,"What is my resting heart rate?","tool_failure",("CLINICAL",),"The requested data could not be retrieved in this example, so I cannot provide a resting-heart-rate value.",(ToolOperation("get_domain_state",("CLINICAL",),{"domain":"CLINICAL"}),),(ToolResult("get_domain_state",("CLINICAL",),(),status="error",error_code="EXECUTION_FAILED"),),(),warnings=("Tool failure: no result available.",)),
      _ex(12,"Explain what hydration means generally.","no_health_data_needed",(),"Hydration describes the body's water balance and fluid intake. No personal health data are needed for this general explanation."),
    ]
