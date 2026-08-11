# Condition Source Refinement

> Date researched: 2026-08-11
> This note refines `CONDITION_KNOWLEDGE_ARCHITECTURE.md`.

## Refined source decision

Use **two complementary sources**, not one database for every job:

1. **Mondo Disease Ontology** = canonical disease identity, aliases, hierarchy and cross-database mapping layer.
2. **MedlinePlus Health Topics** = authoritative patient-facing explanation/content layer.
3. **Project Superhuman ConditionGuidanceRule** = separately curated, source-linked machine-actionable rules for the Interpretation Engine.

The earlier architecture remains correct about keeping reference knowledge separate from personal Data Vault state and keeping educational prose separate from machine-actionable guidance. This note refines only which source should own canonical condition identity.

## Why Mondo belongs underneath MedlinePlus

Mondo exists specifically to harmonize disease terminology from multiple resources. Its official site describes a logic-based unified disease terminology with precise mappings to other disease resources and a hierarchical structure for classification.

At research time its published statistics included roughly 29,000 disease concepts, about 69,000 exact synonyms and over 139,000 database cross-references. It publishes stable OWL, OBO and JSON representations and is licensed under CC BY 4.0.

Official references:

- https://mondo.monarchinitiative.org/
- https://github.com/monarch-initiative/mondo/releases/latest

This makes Mondo a better internal identity than using a display-content page ID as the app's permanent condition ID.

Suggested internal identity:

```text
condition_id = MONDO:xxxxxxx
```

with mapped external identifiers retained when available:

```text
MONDO
MeSH
DOID
Orphanet
ICD (where licence/use permits)
SNOMED CT (where legitimately supplied/licensed)
MedlinePlus topic ID
```

## Why MedlinePlus still matters

Mondo is an ontology, not a patient education product. MedlinePlus is better for what a person actually reads in the app.

Official MedlinePlus health topics cover symptoms, causes, treatment and prevention, and the downloadable XML includes the full topic records for local indexing. NLM publishes health-topic XML Tuesday through Saturday and asks applications using it to attribute MedlinePlus.gov.

Official references:

- https://medlineplus.gov/healthtopics.html
- https://medlineplus.gov/xml.html
- https://medlineplus.gov/xmldescription.html
- https://medlineplus.gov/about/developers/webservices/

## Recommended lookup flow

```text
User types: "asthma"
        |
        v
Local Mondo title + synonym search
        |
        v
MONDO:0004979 / Asthma
        |
        +--> mapped MedlinePlus topic/content
        |
        +--> mapped external codes when useful
        |
        v
Condition detail UI
        |
        v
User chooses Add to my health profile
        |
        v
UserCondition(conditionId = MONDO ID, assertion/status metadata...)
```

If a Mondo concept has no MedlinePlus match, the app can still show the canonical name/definition and authoritative links while marking richer educational content as unavailable.

## Interpretation rule boundary remains mandatory

Neither Mondo nor MedlinePlus should be converted automatically into personalised instructions.

Use an explicit rule layer:

```text
ConditionGuidanceRule
- condition_id
- kind: AVOID | CAUTION | ENCOURAGE | MONITOR | CONTEXT
- target
- applicability
- statement
- evidence_source
- evidence_grade
- region
- last_reviewed_at
- safety_level
```

Machine-actionable rules should be deliberately authored/reviewed from suitable clinical guidance and retain their source and review date. This lets the engine safely suppress inappropriate generic recommendations as well as add relevant context.

## NICE note

NICE guidance is high quality and has a syndication API, but it is not a good global default dependency for Project Superhuman. NICE requires a licence for syndication, international product/service use can carry substantial fees, and AI use of NICE content requires explicit approval/licensing. It can be considered later for a UK-specific licensed guidance pack rather than the global core.

Official references:

- https://www.nice.org.uk/reusing-our-content/nice-syndication-api
- https://www.nice.org.uk/reusing-our-content/use-of-our-content-internationally

## Final stack

```text
Mondo                 = WHAT condition is this?
MedlinePlus            = WHAT does it mean for the user?
UserCondition          = DOES this condition apply to this user?
ConditionGuidanceRule  = HOW may it safely modify Interpretation/Intervention logic?
```

This is the recommended foundation for the Conditions feature.
