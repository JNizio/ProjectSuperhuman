from __future__ import annotations
import argparse, json
from pathlib import Path
from .exporters import export_lines
from .generator import generate_synthetic
from .gold import gold_examples
from .io import read_jsonl, write_jsonl
from .schema import validate_example_dict
from .split import deterministic_split


def combined(): return gold_examples() + generate_synthetic()

def main(argv=None):
    p=argparse.ArgumentParser(prog="trudy-training")
    sub=p.add_subparsers(dest="cmd",required=True)
    v=sub.add_parser("validate"); v.add_argument("path")
    g=sub.add_parser("generate"); g.add_argument("path")
    s=sub.add_parser("split"); s.add_argument("path"); s.add_argument("out_dir"); s.add_argument("--validation-fraction",type=float,default=.2)
    e=sub.add_parser("export"); e.add_argument("path"); e.add_argument("format",choices=["instruction","chat","tool-use"]); e.add_argument("out")
    args=p.parse_args(argv)
    if args.cmd=="validate":
        examples=read_jsonl(args.path); print(json.dumps({"valid":True,"examples":len(examples)})); return 0
    if args.cmd=="generate": write_jsonl(args.path,combined()); print(len(combined())); return 0
    if args.cmd=="split":
        examples=read_jsonl(args.path); train,val=deterministic_split(examples,args.validation_fraction); d=Path(args.out_dir); d.mkdir(parents=True,exist_ok=True); write_jsonl(d/"train.jsonl",train); write_jsonl(d/"validation.jsonl",val); print(json.dumps({"train":len(train),"validation":len(val)})); return 0
    if args.cmd=="export": Path(args.out).write_text(export_lines(read_jsonl(args.path),args.format),encoding="utf-8"); return 0

if __name__=="__main__": raise SystemExit(main())
