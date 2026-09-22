# Project Superhuman — Local Food Database Accuracy Audit (11.4)

## Status

This audit hardens the local food system without replacing the authoritative source library or inventing missing nutrition values.

The local reference database is generated on-device from:
- USDA Foundation Foods 2026-04
- USDA FNDDS 2021-2023
- USDA SR Legacy 2018

The generated Project Superhuman core remains a materialized subset of source-backed rows. Stable source IDs are preserved.

**Important:** the repository does not contain a checked-in copy of the populated ~10,000-row SQLite database. Therefore exact row-level after-counts depend on the user's populated device database. The app now generates those counts automatically from the real SQLite database after USDA bootstrap.

## Before → after

| Area | Before | After |
|---|---|---|
| Full-database audit | No reusable full-row report | Reusable SQLite auditor with JSON + Markdown reports |
| Unknown vs zero | Known flags existed | Preserved; uncertain zero evidence is separately flagged |
| Energy consistency | Macro sanity layer existed | Full-database 4/4/9 discrepancy reporting added; source kcal is not blindly overwritten |
| Unit checks | Import conversion existed | Per-row physical ceilings + micronutrient unit-scale anomaly checks |
| Core eligibility | Coverage/count driven | Adds physical/cross-nutrient plausibility gates |
| Plant classification | Plant identity doubled as diversity eligibility | Classification and diversity eligibility are separate |
| Fungi / algae | Could inherit broad vegetable/plant fallback | Mushroom and seaweed stay distinct unless intentionally redefined |
| Taxonomy | Useful but limited | Expanded deterministic hierarchy and versioned backfill |
| Preparation | Mostly inferred at consumption/evidence layer | Explicit preparation state persisted in SQLite and backfilled |
| Serving reference | Canonical serving values could be lost after insertion | Serving quantity/unit/label persisted |
| Provenance | Source fields existed | Source record ID is preserved through generated core and evidence layers |
| Extra nutrients | Core vitamins/minerals | Adds source-backed water, starch, alcohol, cholesterol, MUFA, PUFA, trans fat, omega-3, omega-6, caffeine when present |
| Duplicate review | Search de-duplication only | Exact/alias/near-identity candidate reporting; no automatic destructive merge |
| Identical profiles | Not systematically reported | Suspicious identical nutrition profiles across different foods are reported |
| Representative checks | Manual/ad hoc | Required representative foods are snapshotted into every audit report |
| Repeatability | One-off integrity helpers | Validator runs against the current SQLite DB and is schema-versioned |

## Database safety

Database version 13 uses additive migrations for recent schemas.

For existing v10+ databases the migration:
- preserves downloaded USDA rows and their IDs;
- adds derived metadata columns only;
- invalidates derived classifier/core/audit metadata so it can be rebuilt deterministically;
- does not delete legitimate source foods;
- does not replace official nutrient values merely to satisfy a validation equation.

Older database schemas that predate the nutrition-integrity model retain the existing rebuild-from-authoritative-source behavior.

## Canonical local food fields now audited

### Identity / provenance
- ID
- canonical/display name
- normalized name
- source
- source record ID
- taxonomy version
- preparation state
- serving quantity
- serving unit
- serving label
- 100 g reference basis

### Macronutrients / energy
- kcal
- protein
- carbohydrate
- fat
- saturated fat
- fibre
- sugars
- sodium
- derived salt

### Micronutrients / additional components
Coverage reporting includes:
- potassium
- calcium
- magnesium
- phosphorus
- iron
- zinc
- copper
- manganese
- selenium
- vitamin A
- vitamin C
- vitamin D
- vitamin E
- vitamin K
- B1
- B2
- B3 / niacin
- B5 / pantothenic acid
- B6
- folate
- B12
- choline
- omega-3
- omega-6
- cholesterol
- caffeine
- water
- starch
- alcohol
- monounsaturated fat
- polyunsaturated fat
- trans fat

A missing nutrient is not converted into zero.

## Row-level validation

The validator reports rather than silently rewriting questionable source evidence.

Checks include:
- negative or non-finite values;
- >100 g/100 g macro/component errors;
- sodium and salt plausibility;
- conservative micronutrient unit-scale ceilings;
- sugars greater than carbohydrate;
- saturated fat greater than total fat;
- implausible total major-nutrient mass;
- macro-derived energy materially exceeding source kcal;
- missing nutrient units;
- source/provenance gaps;
- empty or OTHER-only taxonomy;
- plant/tag contradictions;
- plant-diversity identity inconsistencies;
- processed/composite foods incorrectly diversity-eligible;
- name/preparation disagreement;
- contradictory preparation tags;
- serving quantity/unit/reference inconsistencies;
- food-specific sanity checks for oils, sugars, muscle foods and milk;
- identical nutrition profiles appearing across unrelated names.

The energy check is intentionally asymmetric and conservative. Official/reference kcal is not overwritten simply because 4/4/9 arithmetic differs; fibre, alcohol, organic acids, polyols and source conventions can legitimately affect energy accounting.

## Plant taxonomy

Plant classification and plant-diversity eligibility are now separate concepts.

Examples:
- Apple: plant + fruit; diversity eligible
- Rocket / arugula: plant + vegetable + leafy green; diversity eligible
- Lentil: plant + legume; diversity eligible
- Walnut: plant + nut; diversity eligible
- Flaxseed: plant + seed; diversity eligible
- Olive oil: plant-derived / plant + oil; not automatically diversity eligible
- Oat milk: plant-derived; not automatically diversity eligible
- Mushroom: mushroom/fungi classification, not silently promoted to plant
- Seaweed: seaweed classification, not silently promoted to plant
- Salmon: animal + fish + seafood
- Yogurt: animal + dairy + fermented
- Sourdough: plant + grain + bread + bakery + fermented where the identity is a plain grain bread

The taxonomy is versioned and can be deterministically re-run for all rows when rules change.

## Duplicate policy

No food is automatically deleted or merged.

Candidate detection normalizes:
- capitalization/punctuation;
- selected plurals;
- common aliases such as rocket/arugula, courgette/zucchini, aubergine/eggplant and garbanzo/chickpea.

Preparation state participates in canonical grouping, so meaningful variants such as raw vs boiled/fried/grilled are not treated as the same identity.

Generated `core:usda:` snapshots that share the same source record with a raw USDA row are counted separately as intentional source-snapshot pairs rather than destructive duplicates.

## Representative food verification

Every generated audit report attempts to resolve and snapshot:
- Apple, raw
- Banana, raw
- Rocket / arugula
- Spinach, raw
- Broccoli, raw
- Potato, raw
- Potato, boiled
- Rice, dry/raw
- Rice, cooked
- Lentils
- Chickpeas
- Walnuts
- Flaxseed
- Olive oil
- Chicken breast, raw
- Chicken breast, grilled
- Beef
- Salmon
- Egg, boiled
- Milk, whole
- Greek yogurt, plain
- Cheddar cheese
- Sourdough bread

For each match the report records:
- local ID;
- matched display name;
- source and source-record ID;
- kcal/protein/carbohydrate/fat;
- micronutrient count;
- taxonomy tags;
- preparation state;
- plant-diversity eligibility.

A required representative food that cannot be found is itself an audit warning.

## Runtime reports

After all configured USDA sources have completed, the app creates:

- `filesDir/nutrition_audits/local_food_audit_latest.json`
- `filesDir/nutrition_audits/local_food_audit_latest.md`

The JSON report retains all structured findings. The Markdown report keeps the human-facing output manageable and does not dump the entire database into normal logs.

The report includes:
- total records;
- unique canonical identities;
- core records;
- duplicate candidate groups;
- intentional raw/core snapshot pairs;
- high-confidence records;
- records needing review;
- foods missing useful tags;
- plant foods missing PLANT;
- suspicious plant classifications;
- nutrient-by-nutrient valid/missing/zero/suspicious-zero coverage;
- detailed findings;
- duplicate examples;
- suspicious identical-profile examples;
- representative-food snapshots.

Summary values are also written to `food_reference_meta` for lightweight diagnostics.

## Confidence policy

A row is only counted as high-confidence by the audit when it:
- has no warning/error audit finding;
- retains a source record ID;
- is traceable to USDA source evidence;
- has at least 12 of the selected essential micronutrients;
- has useful deterministic taxonomy.

This is deliberately stricter than “all columns populated.”

## Corrections made by this audit

The audit intentionally avoids mass-editing official nutrition values.

The implemented corrections are structural/evidence corrections:
- source record IDs survive generated-core/evidence transformations;
- imported USDA micronutrients are explicitly marked as reference-database evidence;
- preparation state and serving references are persisted;
- plant classification is separated from diversity eligibility;
- fungi/algae broad-category false positives are blocked;
- total trans fat no longer accepts trans-fat subtype names as if they were total trans fat;
- core selection excludes physically/cross-nutrient implausible records;
- extra source-backed components are retained when USDA provides them.

Questionable source nutrition remains visible for review instead of being replaced by guessed values.

## CI / verification status

The Nutrition workflow now requests:
- Android Kotlin compilation;
- Nutrition unit tests including the new audit and taxonomy cases.

At the time of this audit, GitHub Actions stops before Kotlin compilation because Gradle cannot resolve the existing `app.cash.sqldelight:2.1.0` plugin from the configured plugin repositories. This is a build/dependency-resolution blocker and not a validator test result.

The audit should therefore not be declared fully runtime-verified solely from CI until that dependency-resolution issue is repaired and the populated-device report has been reviewed.

## Final acceptance standard

Do not call the local database “accurate” just because:
- it has ~10,000 rows;
- all fields are populated;
- SQL migrations succeed;
- unit tests execute.

Accept it as trusted only after the generated report has:
1. no unresolved hard physical/unit errors in high-priority generic foods;
2. representative foods resolved to sensible preparation/source identities;
3. suspicious duplicates reviewed;
4. missing-data coverage understood;
5. plant/taxonomy contradictions reviewed;
6. source/provenance gaps resolved or explicitly accepted.

Accurate unknowns remain preferable to fabricated completeness.
