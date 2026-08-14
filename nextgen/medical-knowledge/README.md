# Structured medical condition and symptom corpus

`conditions.v1.json` is the canonical offline reference corpus. It contains deliberately concise,
derived factual summaries and source metadata; it does not contain treatment recommendations.
`symptom-index.v1.json` is generated deterministically from feature records and is not an independent
source of medical truth.

## Safety semantics

- `common`, `possible`, and `uncommon` describe feature placement within a condition record. They do
  not estimate the probability that a user with the symptom has the condition.
- A symptom-index result is a set of candidate educational records, not a differential diagnosis.
- Red flags are condition/context specific and proportionately classified as `emergency`,
  `urgent_same_day`, or `prompt_clinical_review`.
- Empty red-flag lists are valid. The corpus must not inflate routine symptoms into emergencies.
- Investigations describe common diagnostic approaches; they are not instructions to order tests.
- Personal observations and confirmed/user-reported conditions belong in the Data Vault, never here.

## Source strategy

Each record carries one or more direct authoritative references, an evidence scope, and an access
date. Sources are primarily NHS condition pages, with WHO/CDC/NHLBI/NIDDK and major professional
societies used where they provide stronger or more specific coverage. Summaries are original and
compact; source text is not copied in bulk. A clinician/content review remains required before a
production medical release, and the review status is explicit per record.

## Maintenance

Run `python3 nextgen/scripts/validate_medical_corpus.py` after editing. The validator checks schema
shape, stable IDs, referential integrity, provenance, duplicate aliases/features/red flags, forbidden
diagnostic-certainty language, coverage floors, and regenerates/verifies the reverse symptom index.
