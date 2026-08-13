from __future__ import annotations
from dataclasses import asdict, dataclass, field
from hashlib import sha256
import json
from pathlib import Path
from typing import Any

PRECISIONS = {"fp32", "fp16", "bf16"}
QUANTIZATION_MODES = {"none", "4bit", "8bit"}

@dataclass(frozen=True)
class FineTuneConfig:
    base_model: str
    output_dir: str
    tokenizer: str | None = None
    lora_rank: int = 16
    lora_alpha: int = 32
    lora_dropout: float = 0.05
    target_modules: tuple[str, ...] = field(default_factory=lambda: ("q_proj", "k_proj", "v_proj", "o_proj"))
    learning_rate: float = 2e-4
    epochs: float = 2.0
    batch_size: int = 1
    gradient_accumulation: int = 8
    max_sequence_length: int = 2048
    warmup_ratio: float = 0.03
    seed: int = 17
    precision: str = "bf16"
    quantization: str = "4bit"
    gradient_checkpointing: bool = True
    save_steps: int = 100
    logging_steps: int = 10

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> "FineTuneConfig":
        values = dict(data)
        if "target_modules" in values:
            values["target_modules"] = tuple(values["target_modules"])
        cfg = cls(**values)
        cfg.validate()
        return cfg

    @classmethod
    def load(cls, path: str | Path) -> "FineTuneConfig":
        return cls.from_dict(json.loads(Path(path).read_text(encoding="utf-8")))

    def validate(self) -> None:
        errors: list[str] = []
        if not self.base_model.strip(): errors.append("base_model is required")
        if not self.output_dir.strip(): errors.append("output_dir is required")
        if not 1 <= self.lora_rank <= 256: errors.append("lora_rank must be 1..256")
        if self.lora_alpha <= 0: errors.append("lora_alpha must be > 0")
        if not 0.0 <= self.lora_dropout < 1.0: errors.append("lora_dropout must be in [0,1)")
        if not self.target_modules or any(not x.strip() for x in self.target_modules): errors.append("target_modules must be non-empty")
        if self.learning_rate <= 0: errors.append("learning_rate must be > 0")
        if self.epochs <= 0: errors.append("epochs must be > 0")
        if self.batch_size <= 0 or self.gradient_accumulation <= 0: errors.append("batch_size and gradient_accumulation must be > 0")
        if not 128 <= self.max_sequence_length <= 131072: errors.append("max_sequence_length must be 128..131072")
        if not 0.0 <= self.warmup_ratio < 1.0: errors.append("warmup_ratio must be in [0,1)")
        if self.precision not in PRECISIONS: errors.append(f"precision must be one of {sorted(PRECISIONS)}")
        if self.quantization not in QUANTIZATION_MODES: errors.append(f"quantization must be one of {sorted(QUANTIZATION_MODES)}")
        if self.quantization != "none" and self.precision == "fp32": errors.append("quantized training requires fp16 or bf16 compute")
        if errors: raise ValueError("invalid training config: " + "; ".join(errors))

    def canonical_json(self) -> str:
        return json.dumps(asdict(self), sort_keys=True, separators=(",", ":"))

    def config_hash(self) -> str:
        return sha256(self.canonical_json().encode()).hexdigest()
