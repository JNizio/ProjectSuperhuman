# Nutrition Data Reliability — 11.4

This document is the technical reference for the Project Superhuman NextGen Nutrition evidence layer introduced in branch `11.4`.

The product rule is **simple frontend, deep backend**. Normal food logging remains search/scan → food → amount/unit → log. Provenance, knownness, reconciliation, verification and uncertainty remain behind that interaction unless the user needs to act.

## 1. Canonical evidence flow

The implemented flow is:

```text
Bundled / USDA / Open Food Facts / user correction
        ↓
source-specific ingestion boundary
        ↓
NativeFood source record
        ↓
FoodEvidenceEngine.enrich
        ↓
integrity + source classification + verification/confidence
        ↓
CanonicalFoodRecord
        ↓
FoodUnitSystem quantity conversion
        ↓
immutable HealthValue diary snapshot metadata
        ↓
Data Vault / Nutrition analytics / Trudy / N-of-1
```

Source-specific assumptions are confined to ingestion boundaries:

- bundled generic data: `NativeFoodCatalog.loadLocal`;
- USDA Foundation/FNDDS/SR archives: `LargeLocalFoodDatabase`;
- Open Food Facts: `NativeFoodCatalog.parseOpenFoodFactsProduct`;
- local user corrections: `FoodNutritionOverrideStore`.

The rest of the Nutrition experience consumes normalized `NativeFood` evidence and can obtain an immutable `CanonicalFoodRecord` through `FoodEvidenceEngine.canonicalize`.

## 2. Canonical food evidence

`NutritionEvidence.kt` defines the stable evidence vocabulary:

- `FoodIdentityKind`;
- `FoodDataSourceType`;
- `NutrientEvidenceKind`;
- `FoodVerificationState`;
- `FoodDataConfidence`;
- `EnergyEvidenceKind`;
- `FoodPreparationState`;
- `DensityEvidenceSource`;
- `CanonicalNutrientEvidence`;
- `CarbohydrateEvidenceProfile`;
- `CanonicalFoodRecord`.

These types are deliberately categorical. The application does not display arbitrary confidence percentages that imply more precision than the underlying evidence supports.

## 3. Permanent knownness invariant

For every nutrient:

```text
UNKNOWN != ZERO
```

An absent value is not converted to zero evidence.

Examples:

- missing kcal → no `food_kcal` observation;
- explicitly reported 0 kcal → a known `food_kcal = 0` observation;
- missing protein → no protein observation;
- explicitly reported 0 g protein → a known protein observation with value 0.

A neutral `food_entry` metric anchors diary entries. This allows a partially known food to remain visible without inventing a nutrient value merely to keep the diary row alive.

Legacy 11.3 entries remain readable because `food_kcal` is still accepted as the fallback diary anchor.

Bundled food JSON now derives knownness from actual source-field presence instead of constructor defaults.

## 4. Nutrient-level provenance

Food-level source metadata is not sufficient for scientific use. Each persisted nutrient may carry:

- `nutrientEvidenceKind`;
- `nutrientEvidenceSource`;
- `nutrientSourceRecordId`;
- optional `nutrientDerivedFrom`.

Supported evidence kinds include:

- label reported;
- laboratory/reference;
- reference database;
- source reported;
- derived;
- generic inferred;
- user entered;
- missing/unspecified.

A derived sodium value therefore does not masquerade as a manufacturer-reported sodium value.

## 5. Source quality and deterministic reconciliation

`FoodEvidenceEngine.sourcePriority` supplies one central quality ordering rather than scattered UI-specific source checks.

The current hierarchy is context-aware but broadly follows:

1. user-corrected exact record;
2. package-label evidence;
3. manufacturer evidence;
4. USDA Foundation;
5. USDA FNDDS;
6. clean Open Food Facts branded evidence;
7. USDA SR Legacy;
8. Project Superhuman generic reference;
9. unknown/composite fallback.

Search still prioritizes exact textual identity before source quality. Source quality is used as a deterministic tie-breaker rather than causing a loosely related high-quality record to outrank the food the user actually searched for.

`FoodEvidenceEngine.dedupKey` removes trivial generic aliases while preserving:

- barcode identity;
- branded variants;
- meaningful preparation state.

For example, Banana / Bananas / Banana fresh can collapse while dry pasta and cooked pasta remain separate.

The engine intentionally does **not** average conflicting source values.

## 6. Open Food Facts

Barcode lookup uses the OFF v2 product endpoint. Full-text product search remains on OFF's search endpoint because that is the practical search path currently used by the project.

The adapter extracts or retains, where supplied:

- barcode;
- verified English and original product names;
- brand;
- package amount/unit;
- serving amount/unit;
- nutrition basis;
- kcal and kJ;
- protein;
- carbohydrate;
- fat;
- saturated fat;
- fibre;
- sugars;
- salt;
- sodium;
- micronutrients;
- ingredients text;
- allergens;
- additives;
- NOVA group;
- data-quality warnings;
- source language;
- markets/countries;
- source last-modified metadata.

Optional fields remain optional.

OFF available-carbohydrate semantics are explicitly tagged as `AVAILABLE_EXCLUDING_FIBRE`.

### Product-name localisation

The application:

1. prefers a verified explicit English name when OFF supplies one;
2. retains the authentic package/source name;
3. does not machine-translate brands;
4. does not generate literal translated identities;
5. falls back to the authentic source-language name when no verified English name exists.

## 7. Offline branded products

`FoodProductCache.kt` stores the raw branded-product source payload and retrieval timestamp by barcode.

The cache is evidence storage, not a second food model. Cached payloads are reparsed through the same OFF normalization/integrity path.

This provides:

- normal online verification when available;
- previously seen barcode lookup while offline;
- no silent switch to a different nutrition model.

## 8. Carbohydrate semantics

The canonical model preserves the distinction among:

- total carbohydrate including fibre;
- available carbohydrate excluding fibre;
- unknown carbohydrate definition.

`CarbohydrateEvidenceProfile` exposes separate evidence slots for:

- total;
- available;
- fibre;
- sugars;
- added sugars;
- starch;
- polyols.

11.4 does not invent the fields a source does not provide. OFF carbohydrate remains available carbohydrate; USDA carbohydrate-by-difference remains total carbohydrate.

No automatic total↔available conversion is presented as measured evidence.

## 9. Energy evidence

Energy provenance is explicit:

- reported kcal;
- converted kJ;
- macro-derived;
- generic estimate;
- recipe calculation;
- user entered;
- unknown.

Reported label energy is retained even if the macro calculation disagrees. The integrity engine compares evidence and marks a disagreement; it does not silently overwrite the reported energy with a calculated value.

The macro-energy validator is deliberately conservative because alcohol, fibre, polyols and organic acids can contribute energy outside simple 4/4/9 macro arithmetic.

## 10. Integrity validation

`NutritionIntegrity` checks evidence without aggressively deleting unusual but potentially legitimate foods.

Implemented checks include:

- finite/non-negative known values;
- implausible >100 g per 100 g major nutrient values;
- sugars materially exceeding carbohydrate;
- saturated fat materially exceeding total fat;
- macro energy materially exceeding declared energy;
- salt/sodium consistency;
- serving/package quantity plausibility.

Salt/sodium relationship:

```text
salt_g ≈ sodium_mg / 1000 × 2.5
```

When only one is known, the counterpart may be derived and is marked as derived evidence.

The preferred failure mode is:

```text
preserve source evidence → flag → lower confidence
```

not:

```text
silently invent a replacement
```

A narrow corrupt macro can still be suppressed when the evidence strongly supports that one field is the error, preserving the existing 11.3 Capri-Sun-style protection.

## 11. Unit and density model

Physical mass units:

- mg;
- g;
- kg;
- oz;
- lb.

Physical volume units:

- ml;
- cl;
- dl;
- l;
- tsp;
- tbsp;
- cup;
- fl oz.

Resolvable product units:

- serving;
- piece;
- slice;
- scoop;
- bar;
- package;
- bottle;
- can.

Derived units are only exposed when a physical conversion is resolvable from source metadata.

The application never assumes:

```text
1 ml = 1 g
```

Mass↔volume conversion requires defensible density evidence.

Density metadata can retain:

- value;
- whether it is approximate;
- evidence source.

The original user-entered unit and the physical conversion used are retained in the diary snapshot.

## 12. Preparation state

Canonical evidence can represent:

- raw;
- cooked;
- boiled;
- grilled;
- roasted;
- baked;
- fried;
- steamed;
- canned;
- drained;
- frozen;
- dried;
- reconstituted;
- unspecified.

Preparation state participates in deduplication so dry and cooked foods are not merged as aliases.

## 13. Verification and confidence

Verification states include:

- unverified;
- source validated;
- integrity validated;
- label verified;
- user corrected;
- brand verified;
- conflicted.

Confidence states include:

- high;
- medium-high;
- medium;
- low;
- conflicted.

Confidence considers source type, exact product identity, data completeness, integrity warnings, approximation and verification. It is intended for weighting downstream inference, not for cluttering every diary row.

The normal UI only surfaces a quality message when it is actionable, for example:

- Nutrition data may be inaccurate · check label;
- Verified from package label;
- Using your corrected nutrition.

## 14. User corrections

`FoodNutritionOverrideStore` remains a reversible overlay instead of destructively replacing the original external source.

11.4 corrections carry an incrementing revision and are normalized as:

- source type: `USER_CORRECTED`;
- verification: `USER_CORRECTED`;
- confidence: `HIGH`;
- edited nutrients: `USER_ENTERED` evidence.

Removing the override exposes the original source record again.

The current NextGen selected-food card exposes a compact **Correct nutrition data** editor for the common package-label fields. Blank means unknown. A corrected food exposes **Use original source data** to remove the local overlay.

Common-label corrections do not relabel untouched micronutrients as user-entered evidence. Source micronutrients keep their original provenance unless a micronutrient is explicitly overridden.

Corrections remain local; the app does not silently publish them to an external food database.

## 15. Historical immutability

Logging creates a source snapshot in HealthValue metadata.

Snapshot metadata includes, where available:

- canonical schema version;
- food identity kind;
- source type;
- source record ID;
- source revision;
- retrieval timestamp;
- verification state;
- confidence;
- preparation state;
- energy evidence;
- carbohydrate semantics;
- original amount/unit;
- conversion basis;
- density used and density provenance;
- authentic product name;
- source language;
- ingredients/allergens/additives/NOVA;
- integrity/source warnings;
- approximation state.

External database changes therefore affect future searches/logs rather than silently rewriting yesterday's diary evidence.

## 16. Recipes and cooked yield

`RecipeEvidence.kt` introduces ingredient-resolved recipe evidence.

A recipe retains:

- canonical ingredient snapshots;
- ingredient amounts and original units;
- conversion factors;
- servings;
- optional final cooked weight.

Nutrient totals retain coverage:

- known ingredient count;
- total ingredient count;
- whether the total is complete.

Unknown ingredient nutrition is not treated as zero.

When final cooked weight is known, a consumed cooked weight is scaled against the conserved ingredient nutrient total. Cooking yield changes portioning; it does not invent or destroy nutrient evidence.

Composite estimates should remain tagged separately as `COMPOSITE_ESTIMATE`.

## 17. Label evidence and OCR boundary

11.4 includes architecture for:

- front-of-pack evidence;
- nutrition-label evidence;
- ingredient-list evidence;
- OCR candidate extraction.

`FoodLabelOcrGateway` returns candidate values only.

The required future flow is:

```text
image
→ OCR candidates
→ unit normalisation
→ integrity validation
→ user review
→ canonical correction
```

Raw OCR must never write directly into canonical nutrition evidence.

Full production OCR capture/review UI is intentionally not implemented in this pass.

## 18. Data Vault and Trudy

Nutrition remains in the existing HealthValue/Data Vault stream rather than creating a parallel analytics silo.

Downstream systems can inspect snapshot metadata to determine:

- identity;
- source;
- nutrient knownness;
- evidence kind;
- confidence;
- approximation;
- meal time;
- amount/unit;
- source version.

This supports qualified statements such as:

> At least 310 mg was captured, but some logged foods did not report magnesium.

rather than treating missing data as measured zero.

## 19. Performance

Expensive work is concentrated at source ingestion/logging boundaries.

The diary does not refetch external product data to render historical entries.

Existing in-memory search caching remains in place, and branded product source payloads are now persistently cached by barcode for offline reuse.

## 20. Storage and migrations

### User food-correction database

`superhuman_food_overrides.db` is upgraded to schema version 5.

Version 4 added:

- `revision INTEGER NOT NULL DEFAULT 1`.

Version 5 adds durable correction fields for:

- saturated fat;
- salt;
- sodium;
- known/unknown flags for each.

Existing correction rows are retained. Newly added v5 fields migrate as unknown rather than fabricated zero evidence.

### Branded product cache

`superhuman_food_product_cache.db` is new and independent of user diary history.

It stores:

- barcode;
- raw source JSON;
- retrieval timestamp.

### Data Vault / diary

No destructive diary migration is required.

11.4 adds the registered `food_entry` anchor and new explicit nutrient metrics for saturated fat, salt and sodium. Legacy 11.3 `food_kcal`-anchored entries continue to load.

### Large generic reference database

The large USDA/local reference store remains reproducible cache/reference data. No user diary data is stored there.

## 21. Tests

The 11.4 regression suite covers:

- unknown != zero;
- explicit known zero;
- OFF vs USDA carbohydrate semantics;
- source hierarchy;
- generic alias deduplication;
- preparation-state separation;
- conflict confidence;
- immutable snapshot metadata;
- no mass↔volume conversion without density;
- mg / oz / lb / kitchen-volume conversion paths;
- derived serving/product units;
- Capri-Sun-style corrupt macro suppression;
- plausible branded macro preservation;
- sugar > carbohydrate;
- saturated fat > fat;
- salt/sodium derivation;
- salt/sodium conflict;
- ingredient recipe totals;
- cooked recipe yield;
- partial recipe micronutrient coverage.

Primary command:

```bash
gradle --no-daemon -p nextgen :androidApp:testDebugUnitTest
```

## 22. Known limitations after 11.4

The architecture is deliberately truthful about what is not yet verified.

Remaining limitations include:

- production label-photo capture/OCR review is architecture-only;
- no direct manufacturer nutrition API is connected;
- `BRAND_VERIFIED` exists as an evidence state but is not automatically granted without brand/manufacturer evidence;
- no automatic branded-product micronutrient backfill from a generic equivalent;
- no blind cross-source nutrient averaging;
- source reconciliation currently selects/deduplicates evidence rather than constructing synthetic cross-source hybrid foods;
- source-specific preparation/yield factors are only represented when supplied or explicitly entered;
- added-sugar/starch/polyol evidence slots exist, but sources that do not provide them remain unknown;
- complete recipe-management UI is separate from the new recipe evidence/yield core.

These are intentional uncertainty boundaries, not values to fill with guesses.

## 23. Rule for future Nutrition work

When accuracy and apparent completeness conflict:

**preserve uncertainty.**

A record with known calories/protein/fat and unknown magnesium is better evidence than one that silently stores magnesium as 0 mg.

Every future Nutrition feature should preserve:

- identity;
- knownness;
- units;
- source;
- source version;
- evidence kind;
- preparation state;
- approximation;
- historical snapshot meaning.
