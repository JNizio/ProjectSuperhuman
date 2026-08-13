from __future__ import annotations
from dataclasses import asdict, dataclass, field
from typing import Any

SCHEMA_VERSION = "1.1"
DATASET_VERSION = "trudy-specialization-2"
TOOL_CONTRACT_VERSION = "integration-3"
MODEL_POLICY_VERSION = "integration-3"

TASK_CATEGORIES = {
    "no_health_data_needed", "current_state", "metric_history", "trend_interpretation",
    "cross_domain", "insufficient_data", "stale_data", "tool_failure",
    "association_not_causation", "experiment_question", "general_health_context",
    "conflicting_evidence", "metric_domain_disambiguation", "unsupported_request",
    "diagnosis_refusal", "privacy_boundary", "malformed_tool_request", "multi_turn",
}
FORBIDDEN_KEYS = {"chain_of_thought", "reasoning", "hidden_reasoning", "rationale", "scratchpad", "thinking"}
SENSITIVE_KEYS = {"user_id", "device_id", "android_id", "advertising_id", "database_path", "sql", "raw_sql", "api_key", "access_token", "refresh_token", "token", "email", "phone", "imei", "serial_number"}

@dataclass(frozen=True)
class VersionMetadata:
    schema_version: str = SCHEMA_VERSION
    dataset_version: str = DATASET_VERSION
    tool_contract_version: str = TOOL_CONTRACT_VERSION
    model_policy_version: str = MODEL_POLICY_VERSION

@dataclass(frozen=True)
class ConversationTurn:
    role: str
    content: str

@dataclass(frozen=True)
class ToolOperation:
    name: str
    domains: tuple[str, ...] = ()
    arguments: dict[str, Any] = field(default_factory=dict)

@dataclass(frozen=True)
class ToolResult:
    operation_name: str
    domains: tuple[str, ...]
    evidence: tuple[dict[str, Any], ...] = ()
    status: str = "ok"
    error_code: str | None = None

@dataclass(frozen=True)
class EvidenceReference:
    evidence_id: str
    domain: str
    metric_id: str | None = None
    kind: str = "metric"

@dataclass(frozen=True)
class QualityMetadata:
    source: str = "synthetic"
    reviewed: bool = False
    tags: tuple[str, ...] = ()

@dataclass(frozen=True)
class TrainingExample:
    example_id: str
    version: VersionMetadata
    user_request: str
    task_category: str
    domains_involved: tuple[str, ...]
    expected_answer: str
    conversation_context: tuple[ConversationTurn, ...] = ()
    tool_definitions_version: str = TOOL_CONTRACT_VERSION
    expected_tool_operations: tuple[ToolOperation, ...] = ()
    tool_results: tuple[ToolResult, ...] = ()
    expected_evidence_references: tuple[EvidenceReference, ...] = ()
    warnings: tuple[str, ...] = ()
    uncertainty: tuple[str, ...] = ()
    quality: QualityMetadata = field(default_factory=QualityMetadata)

    def to_dict(self) -> dict[str, Any]: return asdict(self)


def _walk(obj: Any, path: str = "") -> list[str]:
    errors: list[str] = []
    if isinstance(obj, dict):
        for key, value in obj.items():
            low = key.lower(); here = f"{path}.{key}" if path else key
            if low in FORBIDDEN_KEYS: errors.append(f"forbidden chain-of-thought field: {here}")
            if low in SENSITIVE_KEYS: errors.append(f"sensitive field forbidden in training data: {here}")
            errors.extend(_walk(value, here))
    elif isinstance(obj, (list, tuple)):
        for i, value in enumerate(obj): errors.extend(_walk(value, f"{path}[{i}]"))
    return errors


def validate_example_dict(data: dict[str, Any]) -> list[str]:
    errors = _walk(data)
    required = {"example_id", "version", "user_request", "task_category", "domains_involved", "expected_answer"}
    missing = sorted(required - data.keys())
    if missing: errors.append("missing required fields: " + ", ".join(missing))
    if data.get("task_category") not in TASK_CATEGORIES: errors.append(f"invalid task_category: {data.get('task_category')}")
    version = data.get("version", {})
    if version.get("schema_version") != SCHEMA_VERSION: errors.append("unsupported schema_version")
    refs = {r.get("evidence_id") for r in data.get("expected_evidence_references", [])}
    supplied: set[str] = set()
    for result in data.get("tool_results", []):
        for evidence in result.get("evidence", []):
            if evidence.get("evidence_id"): supplied.add(evidence["evidence_id"])
    invalid = sorted(ref for ref in refs if ref and ref not in supplied)
    if invalid: errors.append("invalid evidence reference(s): " + ", ".join(invalid))
    domains = set(data.get("domains_involved", []))
    for op in data.get("expected_tool_operations", []):
        op_domains = set(op.get("domains", []))
        if not op_domains.issubset(domains): errors.append("tool operation contains unrequested domain")
        if op.get("name") == "get_context" and not op_domains: errors.append("cross-domain context must list explicit domains")
    tags = set(data.get("quality", {}).get("tags", []))
    if "adversarial" in tags and "eval_only" not in tags: errors.append("adversarial examples must be eval_only")
    return errors
