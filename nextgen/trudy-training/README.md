# Trudy training, evaluation, and model promotion

This directory is the provider-neutral foundation for building and evaluating specialized Trudy models. It does **not** collect real user conversations automatically, expose hidden reasoning, or require a hosted model.

## Hard rules

- Synthetic and explicitly curated examples are the default training source.
- Eval-only adversarial examples must never enter training exports.
- Real health conversations require a future explicit consent + sanitization + de-identification + review workflow before training use.
- No chain-of-thought, scratchpad, hidden rationale, SQL internals, device IDs, API keys, or repository paths belong in datasets or candidate predictions.
- Personal association is not causation. Personal experiments are not universal scientific truth.
- Important trend, association, lag, baseline, and experiment calculations belong in deterministic Trudy tools rather than free-text model arithmetic.
- Scientific evidence stays distinct from personal evidence.
- Trudy does not diagnose or autonomously direct prescription/insulin/risky treatment changes.

## Dataset composition

`gold.py` contains the original curated core cases. `gold_hardening.py` adds integration-4 intelligence and safety cases. `generator.py` creates deterministic synthetic examples with diversified prompt families. `adversarial.py` is evaluation-only and tagged `adversarial` + `eval_only`.

The dataset schema records schema, dataset, tool-contract, and policy versions. `provenance.py` adds generator version, seed, source/category counts, and a canonical SHA-256 content hash.

## Model iteration loop

1. Generate/curate the dataset.
2. Validate privacy/schema/evidence integrity.
3. Audit duplicates, prompt-family leakage, evidence-ID reuse, and category balance.
4. Split train/validation with prompt-family-aware deterministic grouping.
5. Export instruction/chat/tool-use/supervised formats.
6. Train an adapter only in an environment with the optional ML stack installed.
7. Produce strict candidate-prediction JSONL from the baseline and new candidate.
8. Evaluate both against validation + eval-only adversarial suites.
9. Run the configured promotion gate and inspect JSON + Markdown reports.
10. Package for local-runtime conversion only after the gate passes.

## Core commands

From `nextgen/trudy-training`:

```bash
PYTHONPATH=. python -m unittest discover -s tests -v
PYTHONPATH=. python -m trudy_training.cli generate /tmp/trudy/all.jsonl --seed 17 --adversarial-out /tmp/trudy/adversarial.jsonl
PYTHONPATH=. python -m trudy_training.cli validate /tmp/trudy/all.jsonl
PYTHONPATH=. python -m trudy_training.cli audit /tmp/trudy/all.jsonl /tmp/trudy/audit.json
PYTHONPATH=. python -m trudy_training.cli provenance /tmp/trudy/all.jsonl /tmp/trudy/provenance.json --seed 17
PYTHONPATH=. python -m trudy_training.cli split /tmp/trudy/all.jsonl /tmp/trudy/split --validation-fraction 0.2
PYTHONPATH=. python -m trudy_training.cli export /tmp/trudy/split/train.jsonl supervised /tmp/trudy/train.sft.jsonl --training
```

Candidate prediction rows use only:

- `example_id`
- `model_id`
- `tool_operations`
- `answer`
- `evidence_references`
- `warnings`
- `runtime_metadata`

Unknown fields are rejected by the strict promotion path. Hidden reasoning fields are explicitly forbidden.

Promotion example:

```bash
PYTHONPATH=. python -m trudy_training.cli promote \
  /tmp/trudy/adversarial.jsonl \
  /tmp/trudy/baseline.predictions.jsonl \
  /tmp/trudy/candidate.predictions.jsonl \
  config/promotion-policy.json \
  /tmp/trudy/promotion.json \
  --markdown /tmp/trudy/promotion.md
```

The gate is configuration-driven and will not allow strong average performance to erase critical regressions in privacy, fabricated evidence, diagnosis, or causation behavior.

## Fine-tuning smoke behavior

The repository test path does not install or download large models. `smoke.py` can verify config/package-manifest behavior with a fake adapter directory and reports optional `torch`, `transformers`, or `peft` dependencies as missing rather than downloading them. Tokenizer smoke validation is performed only when a tokenizer instance is explicitly available.

The tooling remains vendor-neutral. Candidate base models in roughly the 3B–14B range can be considered later, but license, redistribution/derivative terms, context capacity, language support, tool-use quality, safety behavior, and target-device constraints are all promotion criteria.
