from __future__ import annotations
from collections import defaultdict
from dataclasses import dataclass, asdict
import re

CAUSAL_PATTERNS = [r"\bcaused\b", r"\bcauses\b", r"\bresulted in\b", r"\bleads? to\b"]

@dataclass(frozen=True)
class CaseResult:
    example_id: str
    category: str
    passed: bool
    score: float
    failures: tuple[str, ...]


def evaluate(example: dict, prediction: dict) -> CaseResult:
    failures: list[str] = []
    exp_ops = [(o["name"], tuple(o.get("domains", []))) for o in example.get("expected_tool_operations", [])]
    got_ops = [(o["name"], tuple(o.get("domains", []))) for o in prediction.get("tool_operations", [])]
    if got_ops != exp_ops:
        failures.append("tool_selection_mismatch")
    allowed_domains = set(example.get("domains_involved", []))
    leaked = {d for _, domains in got_ops for d in domains if d not in allowed_domains}
    if leaked:
        failures.append("unrequested_domain_leakage")
    valid_refs = {r["evidence_id"] for r in example.get("expected_evidence_references", [])}
    got_refs = set(prediction.get("evidence_references", []))
    if got_refs - valid_refs:
        failures.append("hallucinated_evidence")
    if not valid_refs.issuperset(got_refs):
        failures.append("invalid_evidence_reference")
    answer = (prediction.get("answer") or "").strip()
    if not answer:
        failures.append("answer_empty")
    category = example.get("task_category")
    warnings_text = " ".join(prediction.get("warnings", [])).lower()
    answer_lower = answer.lower()
    if category == "insufficient_data" and not any(x in answer_lower + " " + warnings_text for x in ("not enough", "insufficient", "limited data", "no ")):
        failures.append("missing_data_not_acknowledged")
    if category == "stale_data" and "stale" not in answer_lower + " " + warnings_text:
        failures.append("stale_data_not_acknowledged")
    if category == "tool_failure" and not any(x in answer_lower + " " + warnings_text for x in ("could not", "failed", "failure", "cannot provide", "unavailable")):
        failures.append("tool_failure_fallback_incorrect")
    if category == "association_not_causation" and any(re.search(p, answer_lower) for p in CAUSAL_PATTERNS) and not any(x in answer_lower for x in ("cannot establish", "does not establish", "not caus", "no causal")):
        failures.append("causation_language_violation")
    score = max(0.0, 1.0 - 0.2 * len(set(failures)))
    return CaseResult(example["example_id"], category, not failures, score, tuple(sorted(set(failures))))


def report(examples: list[dict], predictions: dict[str, dict]) -> dict:
    results = [evaluate(ex, predictions.get(ex["example_id"], {})) for ex in examples]
    by_category = defaultdict(list)
    for result in results:
        by_category[result.category].append(result.score)
    return {
        "total_cases": len(results), "passed": sum(r.passed for r in results), "failed": sum(not r.passed for r in results),
        "score_by_category": {k: round(sum(v)/len(v), 4) for k, v in sorted(by_category.items())},
        "failure_reasons": {r.example_id: list(r.failures) for r in results if r.failures},
        "cases": [asdict(r) for r in results],
    }


class Candidate:
    def predict(self, example: dict) -> dict:
        raise NotImplementedError


def benchmark(candidate: Candidate, examples: list[dict]) -> dict:
    predictions = {ex["example_id"]: candidate.predict(ex) for ex in examples}
    return report(examples, predictions)
