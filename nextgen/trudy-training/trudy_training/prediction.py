from __future__ import annotations
from dataclasses import dataclass, field
from typing import Any

ALLOWED_FIELDS={"example_id","model_id","tool_operations","answer","evidence_references","warnings","runtime_metadata"}
FORBIDDEN_FIELDS={"chain_of_thought","reasoning","hidden_reasoning","rationale","scratchpad","thinking"}

@dataclass(frozen=True)
class CandidatePrediction:
    example_id:str
    model_id:str
    tool_operations:tuple[dict[str,Any],...]=()
    answer:str=""
    evidence_references:tuple[str,...]=()
    warnings:tuple[str,...]=()
    runtime_metadata:dict[str,Any]=field(default_factory=dict)

    def to_dict(self)->dict[str,Any]:
        return {"example_id":self.example_id,"model_id":self.model_id,"tool_operations":list(self.tool_operations),"answer":self.answer,"evidence_references":list(self.evidence_references),"warnings":list(self.warnings),"runtime_metadata":self.runtime_metadata}


def validate_prediction(data:dict[str,Any], strict:bool=True)->list[str]:
    errors=[]
    if not isinstance(data,dict): return ["prediction must be an object"]
    unknown=set(data)-ALLOWED_FIELDS
    forbidden={k for k in data if k.lower() in FORBIDDEN_FIELDS}
    if forbidden: errors.append("forbidden hidden-reasoning field(s): "+", ".join(sorted(forbidden)))
    if strict and unknown: errors.append("unknown prediction field(s): "+", ".join(sorted(unknown)))
    for key in ("example_id","model_id","answer"):
        if not isinstance(data.get(key),str): errors.append(f"{key} must be a string")
    if not isinstance(data.get("tool_operations",[]),list): errors.append("tool_operations must be a list")
    else:
        for i,op in enumerate(data.get("tool_operations",[])):
            if not isinstance(op,dict) or not isinstance(op.get("name"),str) or not isinstance(op.get("domains",[]),list): errors.append(f"tool_operations[{i}] malformed")
    if not isinstance(data.get("evidence_references",[]),list) or not all(isinstance(x,str) for x in data.get("evidence_references",[])): errors.append("evidence_references must be a string list")
    if not isinstance(data.get("warnings",[]),list) or not all(isinstance(x,str) for x in data.get("warnings",[])): errors.append("warnings must be a string list")
    if not isinstance(data.get("runtime_metadata",{}),dict): errors.append("runtime_metadata must be an object")
    return errors
