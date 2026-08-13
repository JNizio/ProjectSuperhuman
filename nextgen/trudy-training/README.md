# Trudy Training Foundation

Offline, provider-neutral infrastructure for future specialization of an open-weight Trudy model. It captures only observable supervised artifacts:

`user request → tool operations → structured tool results/evidence → final answer → warnings/quality labels`

**It must never store chain-of-thought, hidden rationale, scratchpads, or private reasoning traces.**

## Dataset

Canonical source format is deterministic JSONL: one self-contained, versioned example per line. Current metadata pins `schema_version=1.0`, dataset `trudy-starter-1`, and the `integration-2` Trudy tool/model-policy generation.

Starter corpus: 12 curated gold examples plus deterministic synthetic examples spanning Sleep, Body, Exercise, Hydration, Nutrition, Mindfulness, Clinical, sparse/stale data, cross-domain evidence, and identical metric-name collisions.

## Privacy policy

Synthetic and hand-curated examples are the default and preferred training source. **Real user health conversations must never automatically become training data.** Future inclusion of real data requires an explicit, separately designed consent + sanitization + de-identification review workflow. This package rejects obvious identity/device/database/secret fields and intentionally contains no collection/upload code.

Do not store API keys, account IDs, device IDs, raw database paths, SQL internals, private health exports, or model weights in this directory.

## Evaluation

The evaluator is deterministic and rules-first. It checks exact expected tool operations, domain qualification/leakage, evidence-reference validity/hallucination, missing/stale-data honesty, causal-overclaim language, answer presence, and fallback behavior. Reports contain totals, pass/fail counts, scores by category, and per-case failure reasons. An offline `Candidate` interface allows fixtures or future `TrudyModelClient` adapters to be benchmarked without networking.

## Fine-tuning exports

Adapters are deliberately framework-neutral:

- `instruction`: instruction/input/output JSONL
- `chat`: messages JSONL
- `tool-use`: structured request/tools/results/evidence/answer JSONL

They are export views, not new sources of truth.

## Commands

Run from `nextgen/trudy-training` with Python 3.11+ and no third-party packages:

```bash
python -m unittest discover -s tests -v
python -m trudy_training.cli generate data/starter.jsonl
python -m trudy_training.cli validate data/starter.jsonl
python -m trudy_training.cli split data/starter.jsonl data/split --validation-fraction 0.2
python -m trudy_training.cli export data/starter.jsonl chat data/chat.jsonl
python -m trudy_training.cli export data/starter.jsonl tool-use data/tool-use.jsonl
```

## Future LoRA / QLoRA scaffold

The tools are designed for candidate open-weight bases roughly in the **3B–14B** range. Selection must separately evaluate license, redistribution/derivative-model terms, context/tool-use capability, hardware fit, language coverage, and health-safety behavior. No vendor is hardcoded.

A future training runner should consume exported train/validation files and a reviewed configuration, then invoke an external fine-tuning stack (for example a LoRA/QLoRA-capable trainer). Keep framework adapters outside the canonical schema. Quantization, optimizer, sequence length, adapter rank, target modules, batching, and checkpoint policy belong in framework-specific config—not in training examples.

No GPU is assumed. This repository does **not** download models, include model weights, or claim that Trudy has been fine-tuned.
