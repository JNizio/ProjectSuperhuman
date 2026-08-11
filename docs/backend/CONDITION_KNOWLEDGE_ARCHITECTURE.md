# Condition Knowledge Architecture

> Status: recommended architecture for the future Conditions / health-profile knowledge feature.
> Branch: `11.2`
> Date researched: 2026-08-11

## Decision

Use **MedlinePlus Health Topic XML** from the U.S. National Library of Medicine (NLM/NIH) as Project Superhuman's primary v1 condition knowledge catalogue.

Do **not** treat a condition content database as an Interpretation Engine instruction database. Keep educational condition content and machine-actionable guidance as two separate layers.

Recommended shape:

```text
MedlinePlus condition catalogue (reference content)
        |
        v
ConditionKnowledgeRepository
        |
        +------------------> Conditions search / condition detail UI
        |
        v
User chooses "I have this condition"
        |
        v
UserCondition profile record (personal data)
        |
        v
ConditionGuidanceRule layer (curated, sourced rules only)
        |
        v
Interpretation / Intervention / Insights engines
```

## Why MedlinePlus is the v1 source

Official developer information:

- Full health-topic XML is downloadable and intended for application use.
- The health-topic files contain all English and Spanish health topics.
- Records include title, URL, language, source ID, synonyms / "also called" terms, MeSH vocabulary, full summary, topic groups, related topics, equivalent-language topics, primary NIH institute and categorized external links.
- NLM publishes fresh topic XML Tuesday through Saturday.
- The online MedlinePlus Web Service is also free and does not require registration or licensing.
- NLM asks applications using the data to attribute MedlinePlus.gov and not imply endorsement.

Primary references:

- https://medlineplus.gov/xml.html
- https://medlineplus.gov/xmldescription.html
- https://medlineplus.gov/about/developers/webservices/
- https://medlineplus.gov/medlineplus-connect/web-service/

### Why this matches Superhuman

It gives us a compact authoritative catalogue that can be indexed locally for fast search instead of making every condition search depend on a network request.

Useful searchable fields include:

- canonical topic title
- alternate names / synonyms
- MeSH vocabulary
- summary text
- topic group / body-system category
- related conditions

This is enough for an initial screen where a user searches for a condition, reads a concise authoritative explanation and records that the condition applies to them.

## MedlinePlus Web Service and Connect

### MedlinePlus Web Service

Useful as an online fallback or refresh/search service.

Official limits at time of research:

- 85 requests/minute/IP
- NLM recommends caching responses for 12–24 hours

The downloadable XML should be preferred for the main catalogue because it avoids a network dependency for every keystroke/search.

### MedlinePlus Connect

Useful later when Project Superhuman receives coded medical data.

It can map diagnosis/problem codes including:

- ICD-10-CM
- ICD-9-CM
- SNOMED CT

into related MedlinePlus patient information.

That makes it useful for future coded imports, EHR-style data, or a condition record that already has a recognised clinical code.

## NHS Website Content API v2

The NHS Website Content API v2 is a strong **optional UK enrichment layer**, not the core dependency.

It is currently in production and exposes NHS website content including:

- Conditions A to Z
- Symptoms A to Z
- Tests and treatments
- Medicines
- Mental health
- Live Well and other patient-facing material

Official references:

- https://digital.nhs.uk/developer/api-catalogue/nhs-website-content
- https://digital.nhs.uk/developer/api-catalogue/nhs-website-content/v2

Why it should be optional rather than v1 core:

- it requires NHS API onboarding / application credentials
- production use has assurance / terms requirements
- the service is primarily designed around health and care delivery in England

It would nevertheless be valuable later for UK-specific self-care, referral and "when to get help" content.

## ICD-11

WHO's ICD-11 API is useful as a **classification / identifier layer**, not as the main educational or behaviour-guidance source.

Official references:

- https://icd.who.int/docs/icd-api/APIDoc-Version2/
- https://icd.who.int/docs/icd-api/license/

ICD-11 provides stable classification entities and API access, but its purpose is disease classification. It does not provide the rich patient-facing "what this means / what can help / what to avoid" content Superhuman wants.

ICD-11 is currently licensed under CC BY-ND 3.0 IGO, so any use also needs to respect WHO's terms and attribution requirements.

## SNOMED CT

SNOMED CT is an excellent clinical terminology and mapping system but should not become a required v1 dependency until licensing/deployment is deliberately resolved.

Official references:

- https://www.snomed.org/get-snomed
- https://www.snomed.org/licensing

SNOMED International states that use in Member territories generally does not attract SNOMED licensing charges, while non-Member deployment can require annual licensing. Their licensing material also specifically directs mobile-app developers to contact them regarding mobile deployment.

Therefore:

- accept/store SNOMED codes when legitimately supplied
- use MedlinePlus Connect to resolve them where appropriate
- do not ship the entire SNOMED terminology inside Project Superhuman as the default condition database without completing licensing review

## Important safety architecture

### 1. Reference content is not personal health data

The downloaded condition catalogue should live in a dedicated read-only-ish/reference store such as:

```text
ConditionKnowledgeRepository
```

It should **not** be inserted as thousands of fake HealthValues into the Data Vault.

The Data Vault is for the user's observations/profile/history. The condition catalogue is application reference knowledge.

### 2. The user's condition selection is personal data

When a user chooses:

```text
Asthma
```

store a user condition record separately from the static catalogue, for example:

```text
UserCondition(
    conditionId,
    sourceId,
    displayName,
    status,          // active / past / uncertain
    assertionType,   // user-reported / clinically-confirmed / imported
    onsetDate?,
    notes?,
    recordedAt
)
```

Do not silently label a user as diagnosed solely because they searched for a topic.

### 3. Do not convert educational prose directly into medical instructions

The Interpretation Engine should never scrape a sentence from a condition page and automatically turn it into a personalised instruction.

Instead add a deliberately curated rule layer:

```text
ConditionGuidanceRule(
    conditionId,
    ruleId,
    kind,            // AVOID / CAUTION / ENCOURAGE / MONITOR / CONTEXT
    target,          // food, exercise, medication class, sleep behaviour, metric, etc.
    statement,
    applicability,
    evidenceSource,
    evidenceGrade?,
    region?,
    lastReviewedAt,
    safetyLevel
)
```

Examples of the architecture's intent (not medical rules):

```text
Condition -> relevant metric needs closer context
Condition -> exercise recommendation requires a caution
Condition -> dietary suggestion should be suppressed
Condition -> an otherwise-normal biomarker may need different interpretation context
```

Each machine-actionable rule should have an explicit source and review date.

### 4. Interpretation remains cautious

Condition context should modify how Superhuman interprets data, not create diagnoses.

For example the engine may say:

```text
This pattern may be more relevant because condition X is recorded in your profile.
```

It should not say:

```text
Your data proves condition X is worsening.
```

unless a specifically validated clinical feature is later designed for that purpose.

## Recommended local data model

A lightweight catalogue can be updated independently from the personal Data Vault.

Suggested tables/entities:

```text
condition_topic
- id
- source
- source_id
- title
- summary
- url
- language
- updated_at

condition_alias
- condition_id
- alias
- alias_type

condition_external_code
- condition_id
- system        // MeSH / ICD / SNOMED when legitimately available
- code

condition_relation
- condition_id
- related_condition_id
- relation_type

condition_resource
- condition_id
- category
- title
- organisation
- url

condition_guidance_rule
- rule_id
- condition_id
- kind
- target
- statement
- applicability
- evidence_source
- evidence_grade
- region
- last_reviewed_at
- safety_level
```

Use indexed normalized title + alias fields for instant offline search.

## UI flow

Suggested first user-facing flow:

```text
Settings / Health profile
    -> Conditions
        -> Search conditions
        -> type "asth..."
        -> Asthma
        -> condition summary
        -> common/alternate names
        -> authoritative links / related topics
        -> "Add to my health profile"
```

After adding it:

```text
My conditions
- Asthma          Active · user reported
- ...
```

The user should be able to mark a condition as:

- active
- past/resolved
- uncertain / being investigated

and optionally distinguish self-reported from clinician-confirmed/imported.

## Implementation stages

### Stage 1 — catalogue + search

1. Add `ConditionKnowledgeRepository`.
2. Import a packaged MedlinePlus topic snapshot or build an update job that downloads/parses the official XML.
3. Index titles and aliases locally.
4. Add condition search and detail UI.
5. Add attribution.

### Stage 2 — user condition profile

1. Add user condition records.
2. Keep assertion status/source explicit.
3. Back up/restore them with the rest of the user's personal data.
4. Expose condition context to the Interpretation layer through a narrow contract rather than raw SQL.

### Stage 3 — safe Interpretation integration

1. Add `ConditionGuidanceRule`.
2. Start with a small set of heavily sourced, reviewed rules.
3. Add applicability and safety gates.
4. Allow rules to suppress inappropriate generic recommendations as well as surface relevant context.
5. Keep evidence source + last-reviewed metadata visible internally and auditable.

### Stage 4 — optional enrichments

- NHS Website Content API v2 for UK-specific patient content.
- MedlinePlus Connect for imported ICD-10-CM / SNOMED-coded problems.
- ICD-11 identifiers where appropriate and licence-compatible.
- additional specialist sources for rare conditions only when necessary.

## Bottom line

For Project Superhuman v1:

**MedlinePlus = condition knowledge catalogue.**

**Data Vault = the user's condition/profile state.**

**ConditionGuidanceRule = the carefully curated bridge into the Interpretation Engine.**

This separation gives the app useful condition awareness without turning a consumer education database into an unsafe automatic medical-advice engine.
