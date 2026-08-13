from __future__ import annotations
from abc import ABC, abstractmethod
from dataclasses import asdict
import json
from pathlib import Path
from typing import Any

from .formatting import build_loss_mask, render_supervised
from .training_config import FineTuneConfig

class TrainingDependencyError(RuntimeError): pass
class TrainingEnvironmentError(RuntimeError): pass

class TrainingBackend(ABC):
    @abstractmethod
    def train(self, config: FineTuneConfig, train_examples: list[dict], validation_examples: list[dict], system_policy: str) -> dict[str, Any]: ...

class _ListDataset:
    def __init__(self, rows: list[dict[str, Any]]): self.rows = rows
    def __len__(self): return len(self.rows)
    def __getitem__(self, index): return self.rows[index]

class HuggingFacePeftBackend(TrainingBackend):
    """LoRA/QLoRA backend. Imports heavy optional dependencies only when train() is invoked."""

    def train(self, config: FineTuneConfig, train_examples: list[dict], validation_examples: list[dict], system_policy: str) -> dict[str, Any]:
        config.validate()
        if not train_examples: raise ValueError("training dataset is empty")
        try:
            import torch
            from transformers import AutoModelForCausalLM, AutoTokenizer, BitsAndBytesConfig, Trainer, TrainingArguments
            from peft import LoraConfig, TaskType, get_peft_model, prepare_model_for_kbit_training
        except ImportError as exc:
            raise TrainingDependencyError("Training requires torch, transformers, peft and accelerate. Install the optional training requirements.") from exc

        if config.quantization in {"4bit", "8bit"} and not torch.cuda.is_available():
            raise TrainingEnvironmentError("QLoRA/quantized training requires a CUDA-capable GPU in this backend.")
        if config.precision in {"fp16", "bf16"} and not torch.cuda.is_available():
            raise TrainingEnvironmentError(f"{config.precision} training requires a supported accelerator; CPU fallback is intentionally not implicit.")
        if config.quantization != "none":
            try: import bitsandbytes  # noqa: F401
            except ImportError as exc: raise TrainingDependencyError("Quantized training requires bitsandbytes.") from exc

        tokenizer_id = config.tokenizer or config.base_model
        tokenizer = AutoTokenizer.from_pretrained(tokenizer_id, use_fast=True)
        if tokenizer.pad_token_id is None: tokenizer.pad_token = tokenizer.eos_token
        quantization_config = None
        if config.quantization == "4bit":
            quantization_config = BitsAndBytesConfig(load_in_4bit=True, bnb_4bit_quant_type="nf4", bnb_4bit_use_double_quant=True, bnb_4bit_compute_dtype=torch.bfloat16 if config.precision == "bf16" else torch.float16)
        elif config.quantization == "8bit":
            quantization_config = BitsAndBytesConfig(load_in_8bit=True)

        model = AutoModelForCausalLM.from_pretrained(config.base_model, quantization_config=quantization_config, torch_dtype=_dtype(torch, config.precision), device_map="auto" if quantization_config else None)
        if quantization_config is not None: model = prepare_model_for_kbit_training(model, use_gradient_checkpointing=config.gradient_checkpointing)
        if config.gradient_checkpointing: model.gradient_checkpointing_enable()
        model = get_peft_model(model, LoraConfig(r=config.lora_rank, lora_alpha=config.lora_alpha, lora_dropout=config.lora_dropout, target_modules=list(config.target_modules), bias="none", task_type=TaskType.CAUSAL_LM))

        train_rows = [_tokenize(tokenizer, ex, system_policy, config.max_sequence_length) for ex in train_examples]
        eval_rows = [_tokenize(tokenizer, ex, system_policy, config.max_sequence_length) for ex in validation_examples]
        output_dir = Path(config.output_dir); output_dir.mkdir(parents=True, exist_ok=True)
        args = TrainingArguments(output_dir=str(output_dir), num_train_epochs=config.epochs, per_device_train_batch_size=config.batch_size, per_device_eval_batch_size=config.batch_size, gradient_accumulation_steps=config.gradient_accumulation, learning_rate=config.learning_rate, warmup_ratio=config.warmup_ratio, logging_steps=config.logging_steps, save_steps=config.save_steps, seed=config.seed, fp16=config.precision == "fp16", bf16=config.precision == "bf16", evaluation_strategy="epoch" if eval_rows else "no", report_to=[])
        trainer = Trainer(model=model, args=args, train_dataset=_ListDataset(train_rows), eval_dataset=_ListDataset(eval_rows) if eval_rows else None, tokenizer=tokenizer, data_collator=_CausalCollator(tokenizer.pad_token_id))
        result = trainer.train()
        model.save_pretrained(output_dir); tokenizer.save_pretrained(output_dir)
        metadata = {"backend": "huggingface-peft", "train_examples": len(train_rows), "validation_examples": len(eval_rows), "metrics": result.metrics, "config": asdict(config), "config_hash": config.config_hash()}
        (output_dir / "trudy_training_run.json").write_text(json.dumps(metadata, indent=2, sort_keys=True, default=str), encoding="utf-8")
        return metadata

class _CausalCollator:
    def __init__(self, pad_token_id: int): self.pad_token_id = pad_token_id
    def __call__(self, features):
        import torch
        max_len = max(len(x["input_ids"]) for x in features)
        rows = {"input_ids": [], "attention_mask": [], "labels": []}
        for f in features:
            n = max_len - len(f["input_ids"])
            rows["input_ids"].append(f["input_ids"] + [self.pad_token_id] * n)
            rows["attention_mask"].append(f["attention_mask"] + [0] * n)
            rows["labels"].append(f["labels"] + [-100] * n)
        return {k: torch.tensor(v, dtype=torch.long) for k, v in rows.items()}

def _dtype(torch, precision: str):
    return {"fp32": torch.float32, "fp16": torch.float16, "bf16": torch.bfloat16}[precision]

def _tokenize(tokenizer, example: dict, system_policy: str, max_length: int):
    return build_loss_mask(tokenizer, render_supervised(example, system_policy), max_length)
