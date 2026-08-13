from __future__ import annotations
import json
from dataclasses import asdict
from .schema import EvidenceReference, QualityMetadata, ToolOperation, ToolResult, TrainingExample, VersionMetadata

DOMAINS = ("SLEEP", "BODY", "EXERCISE", "HYDRATION", "NUTRITION", "MINDFULNESS", "CLINICAL")


def _metric_example(domain: str, idx: int, stale: bool = False, sparse: bool = False) -> TrainingExample:
    metric = {
        "SLEEP": "sleep_score", "BODY": "body_weight_kg", "EXERCISE": "workout_sessions",
        "HYDRATION": "water_intake_ml", "NUTRITION": "protein_g", "MINDFULNESS": "mindfulness_minutes",
        "CLINICAL": "resting_marker",
    }[domain]
    evidence_id = f"syn-{domain.lower()}-{idx}-metric"
    quality = "stale" if stale else "sparse" if sparse else "good"
    evidence = {"evidence_id": evidence_id, "domain": domain, "metric_id": metric, "value": 70 + idx, "unit": "synthetic_unit", "quality": quality}
    warnings = ("Data is stale.",) if stale else (("Insufficient data for a strong conclusion.",) if sparse else ())
    answer = f"Synthetic {domain.title()} example: the supplied {metric} observation is {70 + idx} synthetic_unit."
    if stale:
        answer += " The data is stale, so confidence should be reduced."
    if sparse:
        answer += " There are too few samples for a reliable trend."
    return TrainingExample(
        example_id=f"synthetic-{domain.lower()}-{idx}-{quality}", version=VersionMetadata(), user_request=f"What is my {domain.lower()} status?",
        task_category="stale_data" if stale else "insufficient_data" if sparse else "current_state",
        domains_involved=(domain,), expected_answer=answer,
        expected_tool_operations=(ToolOperation("get_domain_state", (domain,), {"domain": domain}),),
        tool_results=(ToolResult("get_domain_state", (domain,), (evidence,)),),
        expected_evidence_references=(EvidenceReference(evidence_id, domain, metric),), warnings=warnings,
        quality=QualityMetadata("synthetic", True, (quality,)),
    )


def generate_synthetic(seed: int = 17) -> list[dict]:
    # Seed is part of the public deterministic contract even though generation is currently formulaic.
    out: list[TrainingExample] = []
    for i, domain in enumerate(DOMAINS):
        out.append(_metric_example(domain, seed + i))
        out.append(_metric_example(domain, seed + i + 100, sparse=True))
        out.append(_metric_example(domain, seed + i + 200, stale=True))
    # Cross-domain and same metric-name collision cases.
    a, b = "syn-collision-sleep-score", "syn-collision-body-score"
    out.append(TrainingExample(
        example_id=f"synthetic-cross-domain-{seed}", version=VersionMetadata(), user_request="Compare my recovery signals across sleep and exercise.",
        task_category="cross_domain", domains_involved=("SLEEP", "EXERCISE"),
        expected_answer="Sleep and exercise evidence point in different directions; report both observations without claiming one caused the other.",
        expected_tool_operations=(ToolOperation("get_context", ("SLEEP", "EXERCISE"), {"domains": ["SLEEP", "EXERCISE"]}),),
        tool_results=(ToolResult("get_context", ("SLEEP", "EXERCISE"), (
            {"evidence_id": a, "domain": "SLEEP", "metric_id": "score", "value": 82},
            {"evidence_id": b, "domain": "EXERCISE", "metric_id": "score", "value": 45},
        )),),
        expected_evidence_references=(EvidenceReference(a, "SLEEP", "score"), EvidenceReference(b, "EXERCISE", "score")),
        uncertainty=("Association only; causation is not established.",), quality=QualityMetadata("synthetic", True, ("cross-domain", "metric-name-collision")),
    ))
    return [json.loads(json.dumps(asdict(x))) for x in out]
