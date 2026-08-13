# Trudy fine-tuning pipeline

This folder adapts an open-weight causal language model with LoRA/QLoRA. It does not train a foundation model and it does not automatically ingest real Project Superhuman user data.

## Reproducible flow

```text
curated + deterministic synthetic data
  -> validate
  -> split
  -> supervised/tool-use export
  -> LoRA/QLoRA train
  -> candidate predictions
  -> deterministic adversarial evaluation
  -> candidate comparison
  -> package manifest
```

Run from `nextgen/trudy-training` with `PYTHONPATH=.`:

```bash
python -m trudy_training.cli generate data/all.jsonl --seed 17 --adversarial-out data/adversarial.jsonl
python -m trudy_training.cli validate data/all.jsonl
python -m trudy_training.cli split data/all.jsonl data/split --validation-fraction 0.2
python -m trudy_training.cli export data/split/train.jsonl supervised data/exports/train.sft.jsonl --training
python -m trudy_training.cli train config/lora.example.json data/split/train.jsonl --validation-path data/split/validation.jsonl
python -m trudy_training.cli evaluate data/adversarial.jsonl candidate_predictions.jsonl reports/candidate.json --summary reports/candidate.txt
python -m trudy_training.cli compare reports/baseline.json reports/candidate.json reports/comparison.json
python -m trudy_training.cli package config/lora.example.json reports/candidate.json artifacts/trudy-adapter packages/trudy-candidate.json --license-name '<base-model-license>' --license-source '<license-source>'
```

`train` imports PyTorch, Transformers, PEFT and Accelerate only when invoked. Quantized 4-bit/8-bit training additionally requires bitsandbytes and a CUDA-capable GPU. Missing libraries or unsupported compute fail clearly; there is no pretend-success path.

## Tool format

Tool supervision is provider-neutral and versioned as `trudy-tools-v1`:

```text
<trudy_tool_call>
{"format_version":"trudy-tools-v1","operation":{"name":"get_domain_state","domains":["SLEEP"],"arguments":{"domain":"SLEEP"}}}
</trudy_tool_call>
<trudy_tool_result>
{"format_version":"trudy-tools-v1","result":{...}}
</trudy_tool_result>
<trudy_assistant>
Final evidence-backed answer only.
</trudy_assistant>
```

No hidden chain-of-thought is generated or stored. Observable tool calls are the only supervised intermediate actions.

## Loss masking

`render_supervised()` separates prompt/input from assistant completion. `build_loss_mask()` masks system, user, conversation and supplied tool-input tokens with label `-100`; loss is therefore concentrated on assistant tool calls and final assistant answers. Tool results are currently emitted in the completion stream immediately after the corresponding tool call so a single causal sequence can teach the observable call/result/answer protocol. A backend may later split tool results into non-loss input turns if its chat template supports turn-level masks.

## Dataset

The deterministic generator currently contributes 308 synthetic cases per seed, plus the curated gold set. Coverage includes all core domains, good/sparse/stale/conflicting data, multi-turn examples, explicit cross-domain reads, identical metric names across domains, causation traps, diagnosis traps and domain-qualified metric requests. The separate 12-case adversarial set is tagged `adversarial` and `eval_only`; split, export-for-training and training commands reject evaluation-only rows.

## Evaluation and promotion

The evaluator scores tool operation accuracy, domain correctness, evidence binding, causation safety, diagnosis safety, data-gap acknowledgement, stale-data acknowledgement, explicit cross-domain scope, tool-failure handling, privacy/secret leakage and structured-output validity. It writes machine-readable JSON and a human summary. `compare` reports aggregate/category deltas, new/resolved failures, regressions and improvements. Promotion passes only when aggregate score does not fall, no category regresses and no new failing cases appear.

For Trudy, promotion should weight tool discipline, evidence fidelity and uncertainty behavior above general trivia. Latency and memory footprint must also be measured on the intended device/runtime before shipping. Candidate search should generally focus on roughly 3B–14B open-weight models whose licenses permit the intended distribution; no single model vendor is hardcoded here.

## Candidate package

`package` records base model, LoRA adapter path, tokenizer, dataset/policy/tool-contract versions, SHA-256 training-config hash, evaluation score, supported context length, quantization, license metadata and build timestamp. Model weights are deliberately ignored by git.

## Local-runtime conversion contract

The package manifest is the handoff boundary. Conversion code should consume the base model + adapter + tokenizer and emit a runtime-specific artifact while preserving the manifest and tool-format version. Supported future adapter modules may target:

- llama.cpp / merged or adapter-applied GGUF
- ONNX
- ExecuTorch
- another local inference backend implementing Project Superhuman's `LocalTrudyModelEngine`

Conversion implementations are intentionally separate from training. A conversion must record its backend, artifact hash, quantization and context length rather than mutating the training manifest in place.

## Privacy boundary

Real user health data must never automatically enter this directory. Validation rejects sensitive field names and common secret patterns. File-path guards reject raw database/device-dump style inputs. Do not copy Android databases, Health Connect exports, arbitrary conversation exports, API credentials or device identifiers into training folders. Curated real-world examples, if ever approved, require an explicit de-identification/review process outside this automatic pipeline.
