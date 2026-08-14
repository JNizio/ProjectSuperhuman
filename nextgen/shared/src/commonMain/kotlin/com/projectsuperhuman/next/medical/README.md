# Trudy medical condition corpus

This package defines the typed boundary for the offline medical condition and symptom corpus.
The canonical data lives in `nextgen/medical-knowledge/conditions.v1.json`; its generated reverse
index lives beside it. Corpus facts are reference knowledge, not user observations, diagnoses,
probabilities, treatment rules, or medication advice.

Consumers must preserve feature frequency/context, provenance and red-flag urgency. A symptom
lookup returns candidate educational records only. It must never silently assert that the user has
one of the linked conditions.
