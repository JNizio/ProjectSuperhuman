from __future__ import annotations
import importlib,json,hashlib
from pathlib import Path

def load_training_config(path:str|Path)->dict:
    data=json.loads(Path(path).read_text())
    for key in ("base_model","output_dir"):
        if not data.get(key): raise ValueError(f"missing training config field: {key}")
    return data

def optional_ml_stack_status()->dict:
    status={}
    for name in ("torch","transformers","peft"):
        try: importlib.import_module(name); status[name]="available"
        except ImportError: status[name]="optional-missing"
    return status

def package_manifest(adapter_dir:str|Path,model_id:str)->dict:
    path=Path(adapter_dir); files=sorted(str(p.relative_to(path)) for p in path.rglob("*") if p.is_file()) if path.exists() else []
    return {"model_id":model_id,"adapter_directory":str(path),"file_count":len(files),"file_index_sha256":hashlib.sha256("\n".join(files).encode()).hexdigest()}

def tokenizer_smoke(rendered_samples:list[str],tokenizer=None)->dict:
    if tokenizer is None: return {"status":"skipped","reason":"tokenizer dependency/instance not available","samples":len(rendered_samples)}
    counts=[len(tokenizer(x,add_special_tokens=False)["input_ids"]) for x in rendered_samples]
    return {"status":"ok","samples":len(counts),"max_tokens":max(counts,default=0)}
