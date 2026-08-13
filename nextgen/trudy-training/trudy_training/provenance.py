from __future__ import annotations
from collections import Counter
import hashlib,json

def canonical_bytes(examples:list[dict])->bytes:
    rows=[json.dumps(x,sort_keys=True,separators=(",",":"),ensure_ascii=False) for x in sorted(examples,key=lambda e:e.get("example_id",""))]
    return ("\n".join(rows)+("\n" if rows else "")).encode()

def content_hash(examples:list[dict])->str: return hashlib.sha256(canonical_bytes(examples)).hexdigest()

def build_manifest(examples:list[dict], *, schema_version:str,tool_contract_version:str,policy_version:str,generator_version:str,seed:int,dataset_version:str)->dict:
    sources=Counter(x.get("quality",{}).get("source","unknown") for x in examples); cats=Counter(x.get("task_category","unknown") for x in examples)
    return {"schema_version":schema_version,"dataset_version":dataset_version,"tool_contract_version":tool_contract_version,"policy_version":policy_version,"generator_version":generator_version,"seed":seed,"source_categories":dict(sorted(sources.items())),"task_categories":dict(sorted(cats.items())),"count":len(examples),"content_sha256":content_hash(examples)}

def quality_metrics(examples:list[dict],near_duplicate_count:int=0)->dict:
    cats=Counter(); domains=Counter(); tools=Counter(); warnings=Counter(); kinds=Counter(); turns=Counter(); cross=0
    for ex in examples:
        cats[ex.get("task_category","unknown")]+=1
        for d in ex.get("domains_involved",[]): domains[d]+=1
        for op in ex.get("expected_tool_operations",[]): tools[op.get("name","unknown")]+=1
        for w in ex.get("warnings",[]): warnings[w]+=1
        for r in ex.get("expected_evidence_references",[]): kinds[r.get("kind","metric")]+=1
        turns["multi_turn" if ex.get("conversation_context") else "single_turn"]+=1
        if len(ex.get("domains_involved",[]))>1: cross+=1
    return {"examples_per_category":dict(sorted(cats.items())),"domains":dict(sorted(domains.items())),"tool_operations":dict(sorted(tools.items())),"turns":dict(sorted(turns.items())),"single_domain":len(examples)-cross,"cross_domain":cross,"warning_categories":dict(sorted(warnings.items())),"evidence_kinds":dict(sorted(kinds.items())),"near_duplicate_estimate":near_duplicate_count}
