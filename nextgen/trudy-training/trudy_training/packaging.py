from __future__ import annotations
from dataclasses import asdict, dataclass
from datetime import datetime, timezone
import json
from pathlib import Path
from typing import Any

@dataclass(frozen=True)
class CandidateManifest:
    package_version: str
    base_model: str
    adapter_type: str
    adapter_path: str
    tokenizer: str
    dataset_version: str
    policy_version: str
    tool_contract_version: str
    training_config_hash: str
    eval_score: float
    supported_context_length: int
    quantization: str
    license_name: str
    license_source: str
    build_timestamp: str

    def validate(self) -> None:
        errors=[]
        for name in ("package_version","base_model","adapter_type","adapter_path","tokenizer","dataset_version","policy_version","tool_contract_version","training_config_hash","quantization","license_name","license_source","build_timestamp"):
            if not str(getattr(self,name)).strip(): errors.append(f"{name} is required")
        if not 0.0 <= self.eval_score <= 1.0: errors.append("eval_score must be 0..1")
        if self.supported_context_length < 128: errors.append("supported_context_length must be >= 128")
        if len(self.training_config_hash) != 64: errors.append("training_config_hash must be a SHA-256 hex digest")
        if errors: raise ValueError("invalid candidate manifest: " + "; ".join(errors))

    def to_dict(self) -> dict[str,Any]: self.validate(); return asdict(self)


def build_manifest(*, package_version:str, base_model:str, adapter_path:str, tokenizer:str, dataset_version:str, policy_version:str, tool_contract_version:str, training_config_hash:str, eval_score:float, supported_context_length:int, quantization:str, license_name:str, license_source:str, adapter_type:str="lora") -> CandidateManifest:
    manifest=CandidateManifest(package_version,base_model,adapter_type,adapter_path,tokenizer,dataset_version,policy_version,tool_contract_version,training_config_hash,eval_score,supported_context_length,quantization,license_name,license_source,datetime.now(timezone.utc).isoformat())
    manifest.validate(); return manifest


def write_manifest(path:str|Path, manifest:CandidateManifest)->None:
    Path(path).write_text(json.dumps(manifest.to_dict(),indent=2,sort_keys=True)+"\n",encoding="utf-8")


def load_manifest(path:str|Path)->CandidateManifest:
    manifest=CandidateManifest(**json.loads(Path(path).read_text(encoding="utf-8"))); manifest.validate(); return manifest
