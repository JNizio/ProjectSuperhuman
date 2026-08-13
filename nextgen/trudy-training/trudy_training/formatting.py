from __future__ import annotations
import json
from dataclasses import dataclass
from typing import Any

TOOL_FORMAT_VERSION="trudy-tools-v1"
TOOL_CALL_OPEN="<trudy_tool_call>"; TOOL_CALL_CLOSE="</trudy_tool_call>"
TOOL_RESULT_OPEN="<trudy_tool_result>"; TOOL_RESULT_CLOSE="</trudy_tool_result>"
ASSISTANT_OPEN="<trudy_assistant>"; ASSISTANT_CLOSE="</trudy_assistant>"

@dataclass(frozen=True)
class RenderSegment:
    text:str
    train:bool

@dataclass(frozen=True)
class RenderedSample:
    segments:tuple[RenderSegment,...]
    @property
    def prompt_text(self)->str:
        return "".join(s.text for s in self.segments if not s.train)
    @property
    def completion_text(self)->str:
        return "".join(s.text for s in self.segments if s.train)
    @property
    def text(self)->str: return "".join(s.text for s in self.segments)


def canonical_json(value:Any)->str: return json.dumps(value,sort_keys=True,separators=(",",":"),ensure_ascii=False)

def serialize_tool_call(operation:dict[str,Any])->str:
    return f"{TOOL_CALL_OPEN}\n{canonical_json({'format_version':TOOL_FORMAT_VERSION,'operation':operation})}\n{TOOL_CALL_CLOSE}"

def parse_tool_call(text:str)->dict[str,Any]:
    payload=_parse_block(text,TOOL_CALL_OPEN,TOOL_CALL_CLOSE)
    if payload.get("format_version")!=TOOL_FORMAT_VERSION: raise ValueError("unsupported tool-call format version")
    operation=payload.get("operation")
    if not isinstance(operation,dict) or not operation.get("name"): raise ValueError("malformed tool operation")
    return operation

def serialize_tool_result(result:dict[str,Any])->str:
    return f"{TOOL_RESULT_OPEN}\n{canonical_json({'format_version':TOOL_FORMAT_VERSION,'result':result})}\n{TOOL_RESULT_CLOSE}"

def _parse_block(text:str,opening:str,closing:str)->dict[str,Any]:
    start,end=text.find(opening),text.rfind(closing)
    if start<0 or end<=start: raise ValueError("missing structured block delimiters")
    value=json.loads(text[start+len(opening):end].strip())
    if not isinstance(value,dict): raise ValueError("structured block must contain a JSON object")
    return value

def render_supervised(example:dict[str,Any],system_policy:str)->RenderedSample:
    segments=[RenderSegment("<system>\n"+system_policy.strip()+"\n</system>\n",False)]
    for turn in example.get("conversation_context",[]):
        role=turn.get("role","user"); segments.append(RenderSegment(f"<{role}>\n{str(turn.get('content','')).strip()}\n</{role}>\n",False))
    segments.append(RenderSegment("<user>\n"+example["user_request"].strip()+"\n</user>\n",False))
    operations=example.get("expected_tool_operations",[]); results=example.get("tool_results",[])
    for index,operation in enumerate(operations):
        segments.append(RenderSegment(serialize_tool_call(operation)+"\n",True))
        if index<len(results): segments.append(RenderSegment(serialize_tool_result(results[index])+"\n",False))
    segments.append(RenderSegment(f"{ASSISTANT_OPEN}\n{example['expected_answer'].strip()}\n{ASSISTANT_CLOSE}\n",True))
    return RenderedSample(tuple(segments))

def build_loss_mask(tokenizer,sample:RenderedSample,max_length:int)->dict[str,list[int]]:
    input_ids=[]; labels=[]
    bos=getattr(tokenizer,"bos_token_id",None)
    if bos is not None: input_ids.append(bos); labels.append(-100)
    for segment in sample.segments:
        ids=list(tokenizer(segment.text,add_special_tokens=False)["input_ids"])
        remaining=max_length-len(input_ids)
        if remaining<=0: break
        ids=ids[:remaining]; input_ids.extend(ids); labels.extend(ids if segment.train else [-100]*len(ids))
    eos=getattr(tokenizer,"eos_token_id",None)
    if eos is not None and len(input_ids)<max_length: input_ids.append(eos); labels.append(eos if sample.segments[-1].train else -100)
    return {"input_ids":input_ids,"attention_mask":[1]*len(input_ids),"labels":labels}

def contains_hidden_reasoning(text:str)->bool:
    low=text.lower(); return any(m in low for m in ("chain_of_thought","chain-of-thought","hidden_reasoning","<thinking>","<scratchpad>"))
