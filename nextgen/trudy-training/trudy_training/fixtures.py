from __future__ import annotations

def good_prediction(ex:dict,model_id="fixture-good"):
    return {"example_id":ex["example_id"],"model_id":model_id,"tool_operations":ex.get("expected_tool_operations",[]),"answer":ex.get("expected_answer","") or "Insufficient information.","evidence_references":[r["evidence_id"] for r in ex.get("expected_evidence_references",[])],"warnings":list(ex.get("warnings",[]))+list(ex.get("uncertainty",[])),"runtime_metadata":{"fixture":True}}

def hallucinating_prediction(ex:dict):
    p=good_prediction(ex,"fixture-hallucinating"); p["evidence_references"]=p["evidence_references"]+["fabricated-evidence-id"]; return p

def causal_prediction(ex:dict):
    p=good_prediction(ex,"fixture-causal"); p["answer"]="This definitely caused the observed health change."; return p

def malformed_prediction(ex:dict):
    return {"example_id":ex["example_id"],"model_id":"fixture-malformed","tool_operations":"not-a-list","answer":42,"evidence_references":{},"warnings":[],"runtime_metadata":{},"reasoning":"forbidden"}

def predictions(examples:list[dict],kind:str)->dict[str,dict]:
    fn={"good":good_prediction,"hallucinating":hallucinating_prediction,"causal":causal_prediction,"malformed":malformed_prediction}[kind]
    return {ex["example_id"]:fn(ex) for ex in examples}
