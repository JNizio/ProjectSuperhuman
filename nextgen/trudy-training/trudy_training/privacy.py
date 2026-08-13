from __future__ import annotations
import re
from pathlib import Path
from typing import Any

FORBIDDEN_SUFFIXES={".db",".sqlite",".sqlite3",".realm",".keystore",".jks"}
FORBIDDEN_NAME_FRAGMENTS={"android_dump","device_dump","raw_database","health_export","conversation_export","chat_export"}
SECRET_PATTERNS=[re.compile(r"(?i)(api[_-]?key|access[_-]?token|refresh[_-]?token|secret|bearer)\s*[:=]\s*[^\s,;]{8,}"),re.compile(r"\bAIza[0-9A-Za-z_-]{20,}\b"),re.compile(r"\bsk-[A-Za-z0-9_-]{16,}\b")]
SENSITIVE_FIELD_NAMES={"user_id","device_id","android_id","advertising_id","imei","serial_number","api_key","access_token","refresh_token","database_path","raw_sql"}
ALLOWED_SOURCES={"synthetic","curated","curated-adversarial"}
FORBIDDEN_SOURCE_FRAGMENTS={"export","device","android","database","health_connect","conversation_dump"}

class PrivacyViolation(ValueError): pass

def validate_training_path(path:str|Path)->None:
    p=Path(path); low=p.name.lower()
    if p.suffix.lower() in FORBIDDEN_SUFFIXES: raise PrivacyViolation(f"forbidden training input type: {p.suffix}")
    if any(x in low for x in FORBIDDEN_NAME_FRAGMENTS): raise PrivacyViolation(f"forbidden training input name: {p.name}")

def scan_sensitive(value:Any,path:str="root")->list[str]:
    findings=[]
    if isinstance(value,dict):
        for key,child in value.items():
            here=f"{path}.{key}"
            if key.lower() in SENSITIVE_FIELD_NAMES: findings.append(f"sensitive field: {here}")
            findings.extend(scan_sensitive(child,here))
    elif isinstance(value,(list,tuple)):
        for i,child in enumerate(value): findings.extend(scan_sensitive(child,f"{path}[{i}]") )
    elif isinstance(value,str):
        for pattern in SECRET_PATTERNS:
            if pattern.search(value): findings.append(f"possible secret at {path}")
    return findings

def assert_training_safe(examples:list[dict])->None:
    findings=scan_sensitive(examples)
    for index,example in enumerate(examples):
        quality=example.get("quality",{}); source=str(quality.get("source","")).lower(); tags={str(x).lower() for x in quality.get("tags",[])}
        if source not in ALLOWED_SOURCES: findings.append(f"unapproved training source at root[{index}]: {source or '(missing)'}")
        if any(fragment in source for fragment in FORBIDDEN_SOURCE_FRAGMENTS): findings.append(f"export/device-derived source at root[{index}]")
        if any(tag in tags for tag in {"raw_export","device_dump","conversation_export","health_export"}): findings.append(f"forbidden raw-export tag at root[{index}]")
    if findings: raise PrivacyViolation("; ".join(sorted(set(findings))[:20]))
