from __future__ import annotations
import hashlib


def deterministic_split(examples: list[dict], validation_fraction: float = 0.2, salt: str = "trudy-v1") -> tuple[list[dict], list[dict]]:
    if not 0.0 < validation_fraction < 1.0:
        raise ValueError("validation_fraction must be between 0 and 1")
    train, validation = [], []
    threshold = int(validation_fraction * 10_000)
    for example in sorted(examples, key=lambda e: e["example_id"]):
        digest = hashlib.sha256(f"{salt}:{example['example_id']}".encode()).hexdigest()
        bucket = int(digest[:8], 16) % 10_000
        (validation if bucket < threshold else train).append(example)
    return train, validation
