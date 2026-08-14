# Trudy Nutrition, Hydration and Body Knowledge

## Scope

This package is a read-only knowledge and integration layer for Nutrition, Hydration, Body, Food and common nutrients. It does not create persistence, change the Data Vault schema, redesign module UI, diagnose nutrient deficiencies or prescribe supplement doses.

Package:

`com.projectsuperhuman.next.trudy.nutrition`

## Knowledge coverage

- Energy balance and metabolism
- Protein, carbohydrate, fat, fibre and dietary sugar
- Hydration, heat, exercise, sweating and electrolyte context
- Vitamins, minerals, choline and omega-3 fatty acids
- Meal timing, meal composition, satiety and energy density
- Glycaemic concepts and their limitations
- Dietary patterns, exercise nutrition and recovery nutrition
- Basic digestion-related considerations
- Short-term weight fluctuation, longer-term weight trends and body-composition measurement limits

## Main contracts

### `TrudyNutritionKnowledgeProvider`

The stable consumer-facing boundary. It exposes:

- provenance sources;
- topic and nutrient lookup;
- nutrient alias search;
- locale-aware food resolution;
- question-intent resolution;
- a bounded `NutritionKnowledgeContext` for a question.

Use `CuratedNutritionBodyKnowledgeRepository` as the built-in provider.

### `TrudyNutritionMetricCatalog`

Maps knowledge semantics to existing Project Superhuman metrics. It does not read or write data.

Canonical examples:

| Semantic | Domain | Metric |
| --- | --- | --- |
| Energy intake | Nutrition | `food_kcal` |
| Protein | Nutrition | `food_protein` |
| Carbohydrate | Nutrition | `food_carbs` |
| Fat | Nutrition | `food_fat` |
| Fibre | Nutrition | `food_fibre` |
| Water event | Hydration | `water_intake_ml` |
| Daily water total | Hydration | `water_total_l` |
| Hydration guide | Hydration | `hydration_goal_ml` |
| Body weight | Body | `body_weight_kg` |
| Body fat | Body | `body_fat_pct` |
| Active energy | Exercise | `calories_burned_active_kcal` |
| Exercise duration | Exercise | `exercise_minutes` |
| Heat context | Environment | `environment_temperature_c` |

Micronutrients use the exact dynamic IDs already emitted by `NativeDataHub.saveFood`, for example:

- `food_iron_mg`
- `food_magnesium_mg`
- `food_vitamin_b12_ug`
- `food_vitamin_d_ug`

Dynamic micronutrients are deliberately marked `registryRequired = false` until the central `CoreMetricRegistry` explicitly adopts them. Their absence means unknown because many food sources omit micronutrient values.

Omega-3 knowledge is included, but no dashboard-only or speculative omega-3 metric is invented. The current food ingestion catalogue does not emit one.

### `TrudyNutritionToolPlanner`

Converts a resolved nutrition intent into existing `TrudyToolOperation` requests. It adds no tool protocol and no storage path.

Examples:

- protein adequacy requests bounded protein, energy, body-weight and exercise history;
- hydration requests bounded Hydration plus exercise and environment context;
- overnight weight change requests weight, body-water, carbohydrate and sodium context;
- a broad nutrient-gap question requests bounded Nutrition domain history because micronutrient rows are dynamic.

The integration/orchestration agent should execute these operations through the existing Trudy tool service, then apply deterministic personal trend/association logic where relevant.

## Food terminology

`FoodTerm` separates a canonical concept from natural-language aliases. `FoodAlias.locales` handles regional meaning.

For the bare word `chips`:

- UK locale resolves to chips/fries;
- US locale resolves to potato crisps/chips;
- no locale returns an explicit ambiguity with both candidates.

The catalogue also covers chicken breast, coffee, latte, oats, porridge and protein shakes. Interpretations state which portion, preparation or add-in information must not be assumed.

## Safety boundary

`TrudyNutritionSafetyPolicy` is both a structured constraint list and a model instruction. Integrators must preserve it.

Key rules:

- Food logs can suggest possible dietary coverage gaps but cannot diagnose deficiency, toxicity, malabsorption or a disease.
- Missing food-composition data is unknown, not zero.
- Blood status cannot be inferred from intake rows.
- Supplement doses are not prescribed from this layer.
- Short-term weight change is not automatically fat change.
- Body-composition scale estimates are approximate and condition-sensitive.
- Hydration is contextual; forced or excessive water intake is never encouraged.
- Food and body language stays neutral and does not encourage punitive restriction or obsessive measurement.
- Personal associations do not establish causation.

## Provenance strategy

Every topic and nutrient carries source IDs, and construction fails if a source ID does not resolve. Sources are reviewed with a date and classified by evidence type.

The catalogue prioritises:

- World Health Organization public-health nutrition guidance;
- NHS Eatwell and hydration guidance;
- European Food Safety Authority dietary reference value material;
- NIH Office of Dietary Supplements professional fact sheets;
- CDC heat and hydration guidance;
- professional sports-nutrition position statements;
- peer-reviewed evidence for glycogen/water and bioimpedance limitations.

Reference values are treated as population guidance, not automatic personal targets. The knowledge model intentionally stores qualitative function, source, low-intake context, excess context, population considerations and caveats rather than hard-coding one universal dose.

## Integration sequence

1. Resolve the user question with `contextFor(question, localeHint)`.
2. Preserve `safetyInstructions` and provenance in the model context.
3. Execute `TrudyNutritionToolPlanner.plan(context.intent)` through the existing tool executor.
4. Use the existing deterministic trend and association components for calculations.
5. Distinguish logged evidence, missing data, external reference knowledge and clinical evidence.
6. If a named nutrient is found, include only its relevant record and sources rather than loading the full nutrient catalogue.
7. Do not write a result back as a diagnosis or create new Data Vault rows from the knowledge response.

## Tests

`TrudyNutritionBodyKnowledgeTest` verifies:

- 27 unique provenance-backed nutrients;
- common nutrient aliases;
- UK/US food ambiguity;
- requested question intents;
- exact existing metric IDs;
- no invented omega-3 metric;
- bounded typed tool planning;
- the diagnostic, hydration, weight and disordered-eating safety rules.
