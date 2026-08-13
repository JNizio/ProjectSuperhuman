from __future__ import annotations
import json, random
from dataclasses import asdict
from .schema import ConversationTurn, EvidenceReference, QualityMetadata, ToolOperation, ToolResult, TrainingExample, VersionMetadata

GENERATOR_VERSION="3"
DOMAINS=("SLEEP","BODY","EXERCISE","HYDRATION","NUTRITION","MINDFULNESS","CLINICAL")
METRICS={"SLEEP":"sleep_score","BODY":"body_weight_kg","EXERCISE":"workout_sessions","HYDRATION":"water_intake_ml","NUTRITION":"protein_g","MINDFULNESS":"mindfulness_minutes","CLINICAL":"resting_marker"}
QUALITIES=("good","sparse","stale","conflicting")
PROMPT_STEMS=("Summarize","Review","Describe","Check","What does the stored data say about","Give me the bounded status for","Using only supplied evidence, assess","What can Trudy safely say about")
QUALITY_SUFFIX={"good":"using the current observations","sparse":"without filling in missing samples","stale":"while respecting freshness","conflicting":"without collapsing conflicting signals"}

def _metric_example(domain:str,idx:int,quality:str,rep:int,multi_turn:bool=False)->TrainingExample:
    metric=METRICS[domain]; eid=f"syn-{domain.lower()}-{idx}-{quality}"; value=50+(idx%47)
    evidence={"evidence_id":eid,"domain":domain,"metric_id":metric,"value":value,"unit":"synthetic_unit","quality":quality,"sample_count":1 if quality=="sparse" else 12}
    category={"good":"current_state","sparse":"insufficient_data","stale":"stale_data","conflicting":"conflicting_evidence"}[quality]
    answer=f"The supplied {domain.lower()} observation for {metric} is {value} synthetic_unit."; warnings=(); uncertainty=()
    if quality=="sparse": answer+=" There is not enough data for a reliable trend."; warnings=("Insufficient data.",)
    if quality=="stale": answer+=" The observation is stale, so confidence is reduced."; warnings=("Data is stale.",)
    if quality=="conflicting": answer+=" Other supplied signals conflict, so no single conclusion is justified."; uncertainty=("Conflicting evidence.",)
    prompt=f"{PROMPT_STEMS[rep%len(PROMPT_STEMS)]} my {domain.lower()} {metric.replace('_',' ')} {QUALITY_SUFFIX[quality]}."
    history=(ConversationTurn("user","Use only my stored evidence."),ConversationTurn("assistant","I will keep observations domain-qualified and surface uncertainty.")) if multi_turn else ()
    return TrainingExample(example_id=f"synthetic-{domain.lower()}-{idx}-{quality}{'-mt' if multi_turn else ''}",version=VersionMetadata(),user_request=prompt,task_category="multi_turn" if multi_turn else category,domains_involved=(domain,),expected_answer=answer,conversation_context=history,expected_tool_operations=(ToolOperation("get_domain_state",(domain,),{"domain":domain}),),tool_results=(ToolResult("get_domain_state",(domain,),(evidence,)),),expected_evidence_references=(EvidenceReference(eid,domain,metric),),warnings=warnings,uncertainty=uncertainty,quality=QualityMetadata("synthetic",True,(quality,"prompt-family-v3")+(('multi-turn',) if multi_turn else ())))

def _cross_example(a:str,b:str,idx:int)->TrainingExample:
    ea,eb=f"cross-{idx}-a",f"cross-{idx}-b"; metric="score"
    return TrainingExample(example_id=f"synthetic-cross-{a.lower()}-{b.lower()}-{idx}",version=VersionMetadata(),user_request=f"Compare the supplied {a.lower()} score with the supplied {b.lower()} score, keeping the domains separate.",task_category="cross_domain",domains_involved=(a,b),expected_answer=f"The {a.lower()} and {b.lower()} signals differ. Report both as observations; neither establishes that one caused the other.",expected_tool_operations=(ToolOperation("get_context",(a,b),{"domains":[a,b]}),),tool_results=(ToolResult("get_context",(a,b),({"evidence_id":ea,"domain":a,"metric_id":metric,"value":70+(idx%20)},{"evidence_id":eb,"domain":b,"metric_id":metric,"value":40+(idx%20)})),),expected_evidence_references=(EvidenceReference(ea,a,metric),EvidenceReference(eb,b,metric)),uncertainty=("Association only; causation is not established.",),quality=QualityMetadata("synthetic",True,("cross-domain","metric-name-collision","prompt-family-v3")))

def _trap_example(idx:int,domain:str,kind:str,variant:int)->TrainingExample:
    prompts={"causation":("Exercise definitely caused this improvement, right?","Confirm that the observed change proves exercise caused it."),"diagnosis":("Tell me what disease I have from this data.","Use these measurements to give me a definite diagnosis."),"disambiguation":(f"Show me score for {domain.lower()}, not any other score.",f"Query only the {domain.lower()} version of score; do not use another domain.")}
    if kind=="causation": return TrainingExample(example_id=f"synthetic-causation-{domain.lower()}-{idx}",version=VersionMetadata(),user_request=prompts[kind][variant],task_category="association_not_causation",domains_involved=(domain,),expected_answer="The supplied observations can show an association, but they do not establish causation.",expected_tool_operations=(ToolOperation("get_derived_features",(domain,),{"domain":domain}),),quality=QualityMetadata("synthetic",True,("causation-trap",f"variant-{variant}")))
    if kind=="diagnosis": return TrainingExample(example_id=f"synthetic-diagnosis-{domain.lower()}-{idx}",version=VersionMetadata(),user_request=prompts[kind][variant],task_category="diagnosis_refusal",domains_involved=(domain,),expected_answer="I can describe the stored measurements and uncertainty, but I cannot diagnose a disease from this evidence.",expected_tool_operations=(ToolOperation("get_domain_state",(domain,),{"domain":domain}),),quality=QualityMetadata("synthetic",True,("diagnosis-trap",f"variant-{variant}")))
    return TrainingExample(example_id=f"synthetic-disambiguation-{domain.lower()}-{idx}",version=VersionMetadata(),user_request=prompts[kind][variant],task_category="metric_domain_disambiguation",domains_involved=(domain,),expected_answer=f"I will query score only within {domain.lower()} and will not use identically named metrics from other domains.",expected_tool_operations=(ToolOperation("get_metric_history",(domain,),{"domain":domain,"metricId":"score"}),),quality=QualityMetadata("synthetic",True,("domain-qualified",f"variant-{variant}")))

def generate_synthetic(seed:int=17)->list[dict]:
    rng=random.Random(seed); out=[]
    for domain in DOMAINS:
        for rep in range(8):
            for quality in QUALITIES: out.append(_metric_example(domain,seed+rep*31+rng.randrange(0,13),quality,rep,multi_turn=(rep==7 and quality=="good")))
    idx=0
    for i,a in enumerate(DOMAINS):
        for b in DOMAINS[i+1:]: out.append(_cross_example(a,b,seed+idx)); out.append(_cross_example(b,a,seed+100+idx)); idx+=1
    for i,domain in enumerate(DOMAINS):
        for kind in ("causation","diagnosis","disambiguation"):
            out.append(_trap_example(seed+i,domain,kind,0)); out.append(_trap_example(seed+100+i,domain,kind,1))
    return [json.loads(json.dumps(asdict(x))) for x in out]
