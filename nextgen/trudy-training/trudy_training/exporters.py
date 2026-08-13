from __future__ import annotations
from .adversarial import ensure_excluded_from_training
from .formatting import render_supervised
from .io import canonical_json

DEFAULT_SYSTEM_POLICY="""You are Trudy. Distinguish direct personal observations, derived trends, interpretation and uncertainty. Never claim causation from association. Never invent missing measurements. Surface stale, sparse or conflicting data. Do not diagnose. Use only typed, domain-qualified Trudy health tools. Cite only evidence returned by those tools. Do not expose secrets, SQL, device identifiers or hidden reasoning."""

def export_instruction(example:dict)->dict:
    return {"id":example["example_id"],"instruction":example["user_request"],"input":{"conversation":example.get("conversation_context",[]),"tool_results":example.get("tool_results",[])},"output":example["expected_answer"]}

def export_chat(example:dict)->dict:
    messages=list(example.get("conversation_context",[])); messages.append({"role":"user","content":example["user_request"]}); messages.append({"role":"assistant","content":example["expected_answer"]}); return {"id":example["example_id"],"messages":messages}

def export_tool_use(example:dict)->dict:
    return {"id":example["example_id"],"request":example["user_request"],"expected_tool_operations":example.get("expected_tool_operations",[]),"tool_results":example.get("tool_results",[]),"evidence_references":example.get("expected_evidence_references",[]),"answer":example["expected_answer"],"warnings":example.get("warnings",[]),"uncertainty":example.get("uncertainty",[])}

def export_supervised(example:dict)->dict:
    rendered=render_supervised(example,DEFAULT_SYSTEM_POLICY)
    return {"id":example["example_id"],"text":rendered.text,"segments":[{"text":segment.text,"train":segment.train} for segment in rendered.segments]}

EXPORTERS={"instruction":export_instruction,"chat":export_chat,"tool-use":export_tool_use,"supervised":export_supervised}

def export_lines(examples:list[dict],fmt:str,training:bool=False)->str:
    if fmt not in EXPORTERS: raise ValueError(f"unknown format: {fmt}")
    if training: ensure_excluded_from_training(examples)
    return "\n".join(canonical_json(EXPORTERS[fmt](e)) for e in examples)+("\n" if examples else "")
