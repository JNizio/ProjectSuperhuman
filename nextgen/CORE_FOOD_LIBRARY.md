# Project Superhuman Core Food Library

## Purpose

Project Superhuman 11.4 maintains a persistent local reference database for common foods and ingredients.

The core layer targets **3,000 foods** and is designed to rank ahead of the long-tail USDA reference library in normal Nutrition searches.

It is not a branded-product database. Branded foods continue to use barcode/Open Food Facts flows.

## Authoritative sources

The local importer uses USDA FoodData Central datasets:

- Foundation Foods 2026-04
- FNDDS 2021-2023
- SR Legacy

Each stored record keeps the USDA/FDC source identity in its source metadata.

## Core-food selection

A food can enter the 3,000-food core candidate pool only when these common values are known:

- energy
- protein
- carbohydrate
- total fat
- saturated fat
- fibre
- sugars
- sodium

The essential micronutrient coverage panel contains:

- calcium
- chloride
- copper
- iron
- iodine
- magnesium
- manganese
- phosphorus
- potassium
- selenium
- sodium
- zinc
- vitamin A
- thiamin (B1)
- riboflavin (B2)
- niacin (B3)
- pantothenic acid (B5)
- vitamin B6
- biotin (B7)
- folate (B9)
- vitamin B12
- vitamin C
- vitamin D
- vitamin E
- vitamin K
- choline

Foods with at least 18 known nutrients from this micronutrient panel form the strict quality tier.

The core selector ranks:

1. strict high-coverage foods;
2. common ingredients and everyday foods;
3. preparation variants such as raw, boiled, cooked, baked, roasted, fried and grilled;
4. higher micronutrient completeness;
5. cleaner/shorter generic descriptions.

Niche records such as infant foods, institutional foods and highly specialized formulations are penalized.

If fewer than 3,000 strict-tier foods exist in the available USDA releases, the remaining slots are filled using the highest-coverage candidates. Missing nutrients stay unknown.

## Unknown is not zero

A missing nutrient must never be silently written as 0.

This is especially important for nutrients that are not consistently analyzed in every USDA food, such as iodine, chloride and biotin.

Known zero and unknown are different evidence states.

## Offline behavior

The USDA archives are imported into the app-private SQLite reference database.

After import:

- the core set is fully local;
- core rank is persisted in SQLite;
- food search prioritizes core foods;
- the app does not require a network connection to search already imported foods;
- long-tail USDA records remain available in the same database.

Database schema version 8 stores:

- common macro fields;
- saturated fat;
- sodium;
- known/unknown flags;
- micronutrient JSON;
- total micronutrient count;
- essential micronutrient count;
- core rank.

## Data quality

Core foods are normalized per 100 g unless the underlying reference record uses another supported physical basis.

Natural logging units such as egg, slice, serving or package are conversion layers on top of the canonical physical basis.

No unit conversion may invent a mass, volume or density without an explicit or documented approximation.

## Search behavior

Core foods sort ahead of long-tail reference foods while preserving:

- exact-name priority;
- prefix priority;
- USDA source provenance;
- preparation-state distinctions;
- nutrient completeness.

This means common searches should return useful generic foods before obscure database entries while still keeping the larger USDA library accessible.


## Materialized offline nutrition snapshots

The 3,000 generated Project Superhuman core foods are materialized as independent local records with stable `core:usda:<fdcId>` identifiers.

Each core snapshot stores locally:

- energy;
- protein;
- carbohydrate;
- total fat;
- saturated fat;
- fibre;
- sugars;
- sodium;
- salt;
- every available vitamin and mineral value imported from the authoritative source;
- nutrient units;
- nutrient evidence metadata;
- source/FDC record provenance;
- an explicit list of essential micronutrients for which the source has no reported value.

Search and diary logging do not need to resolve back to the raw USDA row to obtain nutrition values.

The underlying USDA row remains in the long-tail reference database for provenance and source inspection. A missing nutrient in the source is represented as unknown rather than zero.
