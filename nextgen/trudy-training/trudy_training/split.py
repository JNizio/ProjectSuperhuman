from __future__ import annotations
import hashlib,re

def _group_key(example:dict)->str:
    prompt=re.sub(r"\s+"," ",example.get("user_request","").strip().lower())
    prompt=re.sub(r"\b\d+(?:\.\d+)?\b","<n>",prompt)
    category=example.get("task_category",""); domains=",".join(sorted(example.get("domains_involved",[])))
    return f"{category}|{domains}|{prompt}"

def deterministic_split(examples:list[dict],validation_fraction:float=.2,salt:str="trudy-v2")->tuple[list[dict],list[dict]]:
    if not 0.0<validation_fraction<1.0: raise ValueError("validation_fraction must be between 0 and 1")
    train=[]; validation=[]; threshold=int(validation_fraction*10_000); groups={}
    for ex in examples: groups.setdefault(_group_key(ex),[]).append(ex)
    for key in sorted(groups):
        bucket=int(hashlib.sha256(f"{salt}:{key}".encode()).hexdigest()[:8],16)%10_000
        (validation if bucket<threshold else train).extend(sorted(groups[key],key=lambda e:e["example_id"]))
    return train,validation
