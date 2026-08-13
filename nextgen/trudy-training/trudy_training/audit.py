from __future__ import annotations
from collections import Counter,defaultdict
from difflib import SequenceMatcher
import re

def normalize_prompt(text:str)->str:
    text=re.sub(r"\s+"," ",text.strip().lower())
    return re.sub(r"\b\d+(?:\.\d+)?\b","<n>",text)

def exact_duplicates(examples:list[dict])->dict[str,list[str]]:
    groups=defaultdict(list)
    for ex in examples: groups[normalize_prompt(ex.get("user_request",""))].append(ex.get("example_id","?"))
    return {k:v for k,v in groups.items() if len(v)>1}

def near_duplicates(examples:list[dict],threshold:float=.92)->list[tuple[str,str,float]]:
    rows=[]; norms=[(x.get("example_id","?"),normalize_prompt(x.get("user_request",""))) for x in examples]
    for i,(aid,a) in enumerate(norms):
        for bid,b in norms[i+1:]:
            if a==b: continue
            score=SequenceMatcher(None,a,b).ratio()
            if score>=threshold: rows.append((aid,bid,round(score,4)))
    return rows

def overlap(left:list[dict],right:list[dict])->dict[str,list[str]]:
    l={normalize_prompt(x.get("user_request","")):x.get("example_id","?") for x in left}; r={normalize_prompt(x.get("user_request","")):x.get("example_id","?") for x in right}
    return {k:[l[k],r[k]] for k in sorted(set(l)&set(r))}

def audit_dataset(examples:list[dict])->dict:
    cats=Counter(x.get("task_category","?") for x in examples); ids=Counter(x.get("example_id","?") for x in examples)
    evidence=Counter(r.get("evidence_id") for x in examples for r in x.get("expected_evidence_references",[]) if r.get("evidence_id"))
    return {"total":len(examples),"categories":dict(sorted(cats.items())),"duplicate_ids":sorted(k for k,v in ids.items() if v>1),"exact_prompt_duplicate_groups":exact_duplicates(examples),"near_duplicate_count":len(near_duplicates(examples)),"repeated_evidence_ids":sorted(k for k,v in evidence.items() if v>1)}
