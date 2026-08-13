from __future__ import annotations
import argparse, json
from pathlib import Path
from .adversarial import adversarial_examples, ensure_excluded_from_training
from .backends import HuggingFacePeftBackend, TrainingDependencyError, TrainingEnvironmentError
from .evaluator import compare_reports, human_summary, report
from .exporters import DEFAULT_SYSTEM_POLICY, export_lines
from .generator import generate_synthetic
from .gold import gold_examples
from .io import read_jsonl, write_jsonl
from .packaging import build_manifest, write_manifest
from .privacy import assert_training_safe, validate_training_path
from .schema import DATASET_VERSION, MODEL_POLICY_VERSION, TOOL_CONTRACT_VERSION, validate_example_dict
from .split import deterministic_split
from .training_config import FineTuneConfig


def combined(seed:int=17): return gold_examples()+generate_synthetic(seed)

def _validated(path:str)->list[dict]:
    validate_training_path(path); examples=read_jsonl(path); assert_training_safe(examples); errors={e["example_id"]:validate_example_dict(e) for e in examples}; errors={k:v for k,v in errors.items() if v}
    if errors: raise ValueError("dataset validation failed: "+json.dumps(errors,sort_keys=True))
    return examples

def _predictions(path:str)->dict[str,dict]:
    rows=read_jsonl(path); return {row["example_id"]:row.get("prediction",row) for row in rows}

def main(argv=None):
    p=argparse.ArgumentParser(prog="trudy-training"); sub=p.add_subparsers(dest="cmd",required=True)
    v=sub.add_parser("validate"); v.add_argument("path")
    g=sub.add_parser("generate"); g.add_argument("path"); g.add_argument("--seed",type=int,default=17); g.add_argument("--adversarial-out")
    s=sub.add_parser("split"); s.add_argument("path"); s.add_argument("out_dir"); s.add_argument("--validation-fraction",type=float,default=.2)
    e=sub.add_parser("export"); e.add_argument("path"); e.add_argument("format",choices=["instruction","chat","tool-use","supervised"]); e.add_argument("out"); e.add_argument("--training",action="store_true")
    t=sub.add_parser("train"); t.add_argument("config"); t.add_argument("train_path"); t.add_argument("--validation-path")
    ev=sub.add_parser("evaluate"); ev.add_argument("examples"); ev.add_argument("predictions"); ev.add_argument("out_json"); ev.add_argument("--summary")
    c=sub.add_parser("compare"); c.add_argument("baseline_report"); c.add_argument("candidate_report"); c.add_argument("out_json")
    pk=sub.add_parser("package"); pk.add_argument("config"); pk.add_argument("eval_report"); pk.add_argument("adapter_path"); pk.add_argument("out"); pk.add_argument("--package-version",default="1"); pk.add_argument("--license-name",required=True); pk.add_argument("--license-source",required=True)
    args=p.parse_args(argv)
    try:
        if args.cmd=="validate":
            examples=_validated(args.path); print(json.dumps({"valid":True,"examples":len(examples)})); return 0
        if args.cmd=="generate":
            examples=combined(args.seed); write_jsonl(args.path,examples); adv=adversarial_examples();
            if args.adversarial_out: write_jsonl(args.adversarial_out,adv)
            print(json.dumps({"training_examples":len(examples),"adversarial_examples":len(adv),"seed":args.seed})); return 0
        if args.cmd=="split":
            examples=_validated(args.path); ensure_excluded_from_training(examples); train,val=deterministic_split(examples,args.validation_fraction); d=Path(args.out_dir); d.mkdir(parents=True,exist_ok=True); write_jsonl(d/"train.jsonl",train); write_jsonl(d/"validation.jsonl",val); print(json.dumps({"train":len(train),"validation":len(val)})); return 0
        if args.cmd=="export":
            examples=_validated(args.path); Path(args.out).write_text(export_lines(examples,args.format,training=args.training),encoding="utf-8"); print(json.dumps({"examples":len(examples),"format":args.format})); return 0
        if args.cmd=="train":
            config=FineTuneConfig.load(args.config); train=_validated(args.train_path); ensure_excluded_from_training(train); validation=_validated(args.validation_path) if args.validation_path else []; ensure_excluded_from_training(validation); result=HuggingFacePeftBackend().train(config,train,validation,DEFAULT_SYSTEM_POLICY); print(json.dumps(result,sort_keys=True,default=str)); return 0
        if args.cmd=="evaluate":
            examples=_validated(args.examples); result=report(examples,_predictions(args.predictions)); Path(args.out_json).write_text(json.dumps(result,indent=2,sort_keys=True)+"\n",encoding="utf-8"); summary=human_summary(result); 
            if args.summary: Path(args.summary).write_text(summary,encoding="utf-8")
            print(summary,end=""); return 0 if result["failed"]==0 else 2
        if args.cmd=="compare":
            baseline=json.loads(Path(args.baseline_report).read_text()); candidate=json.loads(Path(args.candidate_report).read_text()); result=compare_reports(baseline,candidate); Path(args.out_json).write_text(json.dumps(result,indent=2,sort_keys=True)+"\n"); print(json.dumps(result,sort_keys=True)); return 0 if result["promotion_pass"] else 3
        if args.cmd=="package":
            config=FineTuneConfig.load(args.config); eval_report=json.loads(Path(args.eval_report).read_text()); manifest=build_manifest(package_version=args.package_version,base_model=config.base_model,adapter_path=args.adapter_path,tokenizer=config.tokenizer or config.base_model,dataset_version=DATASET_VERSION,policy_version=MODEL_POLICY_VERSION,tool_contract_version=TOOL_CONTRACT_VERSION,training_config_hash=config.config_hash(),eval_score=float(eval_report.get("aggregate_score",0.0)),supported_context_length=config.max_sequence_length,quantization=config.quantization,license_name=args.license_name,license_source=args.license_source); write_manifest(args.out,manifest); print(json.dumps(manifest.to_dict(),sort_keys=True)); return 0
    except (ValueError,TrainingDependencyError,TrainingEnvironmentError) as exc:
        p.exit(2,f"trudy-training: {exc}\n")

if __name__=="__main__": raise SystemExit(main())
