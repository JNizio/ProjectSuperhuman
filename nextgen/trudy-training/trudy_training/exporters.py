from __future__ import annotations
from .io import canonical_json


def export_instruction(example: dict) -> dict:
    return {"id": example["example_id"], "instruction": example["user_request"], "input": {"conversation": example.get("conversation_context", []), "tool_results": example.get("tool_results", [])}, "output": example["expected_answer"]}


def export_chat(example: dict) -> dict:
    messages = list(example.get("conversation_context", []))
    messages.append({"role": "user", "content": example["user_request"]})
    messages.append({"role": "assistant", "content": example["expected_answer"]})
    return {"id": example["example_id"], "messages": messages}


def export_tool_use(example: dict) -> dict:
    return {"id": example["example_id"], "request": example["user_request"], "expected_tool_operations": example.get("expected_tool_operations", []), "tool_results": example.get("tool_results", []), "evidence_references": example.get("expected_evidence_references", []), "answer": example["expected_answer"], "warnings": example.get("warnings", []), "uncertainty": example.get("uncertainty", [])}

EXPORTERS = {"instruction": export_instruction, "chat": export_chat, "tool-use": export_tool_use}


def export_lines(examples: list[dict], fmt: str) -> str:
    if fmt not in EXPORTERS:
        raise ValueError(f"unknown format: {fmt}")
    return "\n".join(canonical_json(EXPORTERS[fmt](e)) for e in examples) + ("\n" if examples else "")
