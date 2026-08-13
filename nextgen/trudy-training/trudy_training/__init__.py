"""Offline, provider-neutral Trudy training and evaluation foundation."""
from .gold import gold_examples
from .generator import generate_synthetic
from .evaluator import evaluate, report, benchmark, Candidate
