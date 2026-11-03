# Generic food micronutrient enrichment (11.4)

## Scope
The bundled `app/src/main/assets/food_db.js` contains 157 approximate generic entries. Their
existing macro values, names, IDs, quantities and user overrides are unchanged. The Android
catalogue now supplements eligible entries from food-composition records already imported
into the device's USDA FoodData Central SQLite reference library.

## Source and matching
- Source data: existing USDA Foundation Foods, FNDDS and SR Legacy records imported by
  `LargeLocalFoodDatabase`. All nutrient quantities come directly from those records.
- The matcher requires a traceable USDA source record, at least six actual micronutrients,
  matching core food identity and preparation, and compatible calories/protein/fat.
- It rejects potentially misleading matches from another product type, fortification,
  mixed-food ingredient, preparation or a materially divergent equally ranked profile.
- For 100-ml foods, values are converted from source per-100-g using the food's
  known density; without density, no conversion is attempted.
- Matched nutrients retain the USDA reference's record ID and source name.
  The canonical evidence kind is DERIVED because the profile is representative
  rather than measured for this particular bundled entry.
- Absent or ambiguous nutrients remain **unknown**, not zero. Recipe- and brand-dependent
  foods are intentionally excluded when no trustworthy generic profile exists.

## Availability and performance
- A source profile is available only once the underlying USDA archive has imported;
  the small bundled catalogue does not invent its own nutrient constants offline.
- Interactive search uses its already-retrieved local USDA candidates; it does not
  perform 157 matching queries on every keystroke.
- Full-catalogue enrichment happens on an IO dispatcher and is cached by USDA row count,
  to refresh after an archive import without repeating work on every render.
- Existing food logs are left intact; newly logged entries capture matched nutrients
  with evidence provenance. User-edited nutrition overrides remain higher priority.

## Verification
Unit tests cover source provenance, wrong food identity, incompatible cooking states,
non-standard mixed dishes, missing data and conversion from grams to millilitres.
Run the Android test suite and verify on-device with a completed USDA import:
apple; boiled potato; milk 2%; and a mixed recipe that should retain unknown values.

## Limitation
No claim is made that every one of the 157 bundled entries has a match. A reliable
micronutrient panel for a branded or variable recipe requires its actual ingredient
formula or source nutrition record. Do not fill such gaps with estimates disguised as
measured data.
