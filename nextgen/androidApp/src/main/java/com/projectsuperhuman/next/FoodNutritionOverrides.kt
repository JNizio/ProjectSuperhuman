package com.projectsuperhuman.next

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONObject

internal data class EditableNutrientDef(
    val id: String,
    val label: String,
    val unit: String
)

internal val editableFoodNutrients = listOf(
    EditableNutrientDef("calcium", "Calcium", "mg"),
    EditableNutrientDef("chloride", "Chloride", "mg"),
    EditableNutrientDef("copper", "Copper", "mg"),
    EditableNutrientDef("iron", "Iron", "mg"),
    EditableNutrientDef("iodine", "Iodine", "µg"),
    EditableNutrientDef("magnesium", "Magnesium", "mg"),
    EditableNutrientDef("manganese", "Manganese", "mg"),
    EditableNutrientDef("phosphorus", "Phosphorus", "mg"),
    EditableNutrientDef("potassium", "Potassium", "mg"),
    EditableNutrientDef("selenium", "Selenium", "µg"),
    EditableNutrientDef("sodium", "Sodium", "mg"),
    EditableNutrientDef("zinc", "Zinc", "mg"),
    EditableNutrientDef("vitamin_a", "Vitamin A", "µg"),
    EditableNutrientDef("vitamin_b1", "Vitamin B1", "mg"),
    EditableNutrientDef("vitamin_b2", "Vitamin B2", "mg"),
    EditableNutrientDef("niacin", "Niacin (B3)", "mg"),
    EditableNutrientDef("pantothenic_acid", "Pantothenic acid (B5)", "mg"),
    EditableNutrientDef("vitamin_b6", "Vitamin B6", "mg"),
    EditableNutrientDef("folate", "Folate (B9)", "µg"),
    EditableNutrientDef("vitamin_b12", "Vitamin B12", "µg"),
    EditableNutrientDef("biotin", "Biotin (B7)", "µg"),
    EditableNutrientDef("vitamin_c", "Vitamin C", "mg"),
    EditableNutrientDef("vitamin_d", "Vitamin D", "µg"),
    EditableNutrientDef("vitamin_e", "Vitamin E", "mg"),
    EditableNutrientDef("vitamin_k", "Vitamin K", "µg"),
    EditableNutrientDef("choline", "Choline", "mg")
)

/**
 * Persistent user corrections for food nutrition data.
 *
 * Open Food Facts and bundled reference foods remain the source records. A user correction is kept
 * separately and layered over the source whenever that product is loaded again. Barcode is used as
 * the strongest identity when available; generic foods fall back to their stable Project Superhuman id.
 */
internal object FoodNutritionOverrideStore {
    @Volatile private var appContext: Context? = null

    fun attach(context: Context) {
        appContext = context.applicationContext
    }

    fun save(context: Context, original: NativeFood, corrected: NativeFood) {
        attach(context)
        FoodNutritionOverrideDb(context.applicationContext).use { helper ->
            val key = identityKey(original)
            val editedFields = buildSet {
                addAll(original.correctedFields)
                fun changed(id: String, oldValue: Double, oldKnown: Boolean, newValue: Double, newKnown: Boolean) {
                    if (oldKnown != newKnown || (newKnown && kotlin.math.abs(oldValue - newValue) > 1e-9)) add(id)
                }
                changed("energy_kcal", original.kcal, original.kcalKnown, corrected.kcal, corrected.kcalKnown)
                changed("protein", original.protein, original.proteinKnown, corrected.protein, corrected.proteinKnown)
                changed("carbohydrate", original.carbs, original.carbsKnown, corrected.carbs, corrected.carbsKnown)
                changed("fat", original.fat, original.fatKnown, corrected.fat, corrected.fatKnown)
                changed("fibre", original.fibre, original.fibreKnown, corrected.fibre, corrected.fibreKnown)
                changed("sugars", original.sugar, original.sugarKnown, corrected.sugar, corrected.sugarKnown)
                changed("saturated_fat", original.saturatedFat, original.saturatedFatKnown, corrected.saturatedFat, corrected.saturatedFatKnown)
                changed("salt", original.salt, original.saltKnown, corrected.salt, corrected.saltKnown)
                changed("sodium", original.sodiumMg, original.sodiumKnown, corrected.sodiumMg, corrected.sodiumKnown)
            }
            if (editedFields.isEmpty()) return@use

            val revision = helper.readableDatabase.rawQuery(
                "SELECT revision FROM food_nutrition_override WHERE identity_key = ? LIMIT 1",
                arrayOf(key)
            ).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) + 1 else 1 }
            val values = ContentValues().apply {
                put("identity_key", key)
                put("food_id", corrected.id)
                put("barcode", corrected.barcode.orEmpty())
                put("name", corrected.name)
                put("kcal", corrected.kcal)
                put("kcal_known", if (corrected.kcalKnown) 1 else 0)
                put("protein", corrected.protein)
                put("carbs", corrected.carbs)
                put("fat", corrected.fat)
                put("fibre", corrected.fibre)
                put("sugar", corrected.sugar)
                put("saturated_fat", corrected.saturatedFat)
                put("salt", corrected.salt)
                put("sodium_mg", corrected.sodiumMg)
                put("protein_known", if (corrected.proteinKnown) 1 else 0)
                put("carbs_known", if (corrected.carbsKnown) 1 else 0)
                put("fat_known", if (corrected.fatKnown) 1 else 0)
                put("fibre_known", if (corrected.fibreKnown) 1 else 0)
                put("sugar_known", if (corrected.sugarKnown) 1 else 0)
                put("saturated_fat_known", if (corrected.saturatedFatKnown) 1 else 0)
                put("salt_known", if (corrected.saltKnown) 1 else 0)
                put("sodium_known", if (corrected.sodiumKnown) 1 else 0)
                put("micronutrients_json", encodeMicros(corrected.micronutrients))
                put("updated_epoch_ms", System.currentTimeMillis())
                put("revision", revision)
                put("edited_fields", editedFields.sorted().joinToString("|"))
            }
            helper.writableDatabase.insertWithOnConflict(
                "food_nutrition_override",
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE
            )
        }
    }

    fun remove(context: Context, food: NativeFood) {
        attach(context)
        FoodNutritionOverrideDb(context.applicationContext).use { helper ->
            helper.writableDatabase.delete(
                "food_nutrition_override",
                "identity_key = ?",
                arrayOf(identityKey(food))
            )
        }
    }

    fun hasOverride(context: Context, food: NativeFood): Boolean {
        attach(context)
        FoodNutritionOverrideDb(context.applicationContext).use { helper ->
            helper.readableDatabase.rawQuery(
                "SELECT 1 FROM food_nutrition_override WHERE identity_key = ? LIMIT 1",
                arrayOf(identityKey(food))
            ).use { return it.moveToFirst() }
        }
    }

    fun applyAll(context: Context, foods: List<NativeFood>): List<NativeFood> {
        attach(context)
        if (foods.isEmpty()) return foods
        FoodNutritionOverrideDb(context.applicationContext).use { helper ->
            val db = helper.readableDatabase
            return foods.map { food -> applyFromDb(db, food) }
        }
    }

    fun applyIfAttached(food: NativeFood): NativeFood {
        val context = appContext ?: return food
        FoodNutritionOverrideDb(context).use { helper ->
            return applyFromDb(helper.readableDatabase, food)
        }
    }

    private fun applyFromDb(db: SQLiteDatabase, food: NativeFood): NativeFood {
        db.rawQuery(
            """
            SELECT kcal, protein, carbs, fat, fibre, sugar,
                   kcal_known, protein_known, carbs_known, fat_known, fibre_known, sugar_known,
                   micronutrients_json, updated_epoch_ms, revision,
                   saturated_fat, salt, sodium_mg,
                   saturated_fat_known, salt_known, sodium_known,
                   edited_fields
            FROM food_nutrition_override
            WHERE identity_key = ?
            LIMIT 1
            """.trimIndent(),
            arrayOf(identityKey(food))
        ).use { cursor ->
            if (!cursor.moveToFirst()) return food

            val correctionEpochMs = cursor.getLong(13)
            val revision = cursor.getInt(14)
            val editedFields = cursor.getString(21)
                .split('|')
                .map(String::trim)
                .filter(String::isNotBlank)
                .toSet()
            fun use(id: String) = id in editedFields

            val correctedMicros = decodeMicros(cursor.getString(12)).mapValues { (_, nutrient) ->
                nutrient.copy(
                    evidenceKind = NutrientEvidenceKind.USER_ENTERED,
                    source = "User correction",
                    sourceRecordId = identityKey(food)
                )
            }
            val resolvedMicros = food.micronutrients + correctedMicros

            val resolved = food.copy(
                kcal = if (use("energy_kcal")) cursor.getDouble(0) else food.kcal,
                kcalKnown = if (use("energy_kcal")) cursor.getInt(6) != 0 else food.kcalKnown,
                protein = if (use("protein")) cursor.getDouble(1) else food.protein,
                carbs = if (use("carbohydrate")) cursor.getDouble(2) else food.carbs,
                fat = if (use("fat")) cursor.getDouble(3) else food.fat,
                fibre = if (use("fibre")) cursor.getDouble(4) else food.fibre,
                sugar = if (use("sugars")) cursor.getDouble(5) else food.sugar,
                proteinKnown = if (use("protein")) cursor.getInt(7) != 0 else food.proteinKnown,
                carbsKnown = if (use("carbohydrate")) cursor.getInt(8) != 0 else food.carbsKnown,
                fatKnown = if (use("fat")) cursor.getInt(9) != 0 else food.fatKnown,
                fibreKnown = if (use("fibre")) cursor.getInt(10) != 0 else food.fibreKnown,
                sugarKnown = if (use("sugars")) cursor.getInt(11) != 0 else food.sugarKnown,
                saturatedFat = if (use("saturated_fat")) cursor.getDouble(15) else food.saturatedFat,
                salt = if (use("salt")) cursor.getDouble(16) else food.salt,
                sodiumMg = if (use("sodium")) cursor.getDouble(17) else food.sodiumMg,
                saturatedFatKnown = if (use("saturated_fat")) cursor.getInt(18) != 0 else food.saturatedFatKnown,
                saltKnown = if (use("salt")) cursor.getInt(19) != 0 else food.saltKnown,
                sodiumKnown = if (use("sodium")) cursor.getInt(20) != 0 else food.sodiumKnown,
                micronutrients = resolvedMicros
            )

            val integrity = NutritionIntegrity.validateFoodValues(
                basisAmount = resolved.basisAmount,
                basisUnit = resolved.basisUnit,
                kcal = resolved.kcal,
                kcalKnown = resolved.kcalKnown,
                protein = resolved.protein,
                proteinKnown = resolved.proteinKnown,
                carbs = resolved.carbs,
                carbsKnown = resolved.carbsKnown,
                carbohydrateDefinition = resolved.carbohydrateDefinition,
                fat = resolved.fat,
                fatKnown = resolved.fatKnown,
                saturatedFat = resolved.saturatedFat,
                saturatedFatKnown = resolved.saturatedFatKnown,
                fibre = resolved.fibre,
                fibreKnown = resolved.fibreKnown,
                sugar = resolved.sugar,
                sugarKnown = resolved.sugarKnown,
                saltG = resolved.salt,
                saltKnown = resolved.saltKnown,
                sodiumMg = resolved.sodiumMg,
                sodiumKnown = resolved.sodiumKnown,
                servingQuantity = resolved.servingQuantity,
                productQuantity = resolved.productQuantity
            )

            val editedSource = if (food.source.contains("edited locally", ignoreCase = true)) {
                food.source
            } else {
                food.source + " · edited locally"
            }
            fun resolvedKnown(id: String): Boolean = when (id) {
                "energy_kcal" -> resolved.kcalKnown
                "protein" -> resolved.proteinKnown
                "carbohydrate" -> resolved.carbsKnown
                "fat" -> resolved.fatKnown
                "fibre" -> resolved.fibreKnown
                "sugars" -> resolved.sugarKnown
                "saturated_fat" -> resolved.saturatedFatKnown
                "salt" -> resolved.saltKnown
                "sodium" -> resolved.sodiumKnown
                else -> true
            }
            val evidence = food.nutrientEvidence.toMutableMap().apply {
                editedFields.forEach { id ->
                    put(
                        id,
                        if (resolvedKnown(id)) NutrientEvidenceKind.USER_ENTERED else NutrientEvidenceKind.MISSING
                    )
                }
            }
            val coreFields = setOf("energy_kcal", "protein", "carbohydrate", "fat")
            val remainingApproximate = food.nutritionApproximate && !editedFields.containsAll(coreFields)

            return resolved.copy(
                nutritionIntegrityWarning = integrity.warningText,
                source = editedSource,
                sourceType = FoodDataSourceType.USER_CORRECTED,
                sourceRevision = "user-correction-v" + revision + "@" + correctionEpochMs,
                verificationState = if (integrity.conflicted) {
                    FoodVerificationState.CONFLICTED
                } else {
                    FoodVerificationState.USER_CORRECTED
                },
                confidence = if (integrity.conflicted) {
                    FoodDataConfidence.CONFLICTED
                } else if (remainingApproximate) {
                    FoodDataConfidence.MEDIUM
                } else {
                    FoodDataConfidence.HIGH
                },
                energyEvidence = if (use("energy_kcal")) {
                    if (resolved.kcalKnown) EnergyEvidenceKind.USER_ENTERED else EnergyEvidenceKind.UNKNOWN
                } else {
                    food.energyEvidence
                },
                nutrientEvidence = evidence,
                correctedFields = editedFields,
                sourceWarnings = integrity.warnings,
                nutritionApproximate = remainingApproximate
            )
        }
    }

    private fun identityKey(food: NativeFood): String =
        food.barcode?.filter(Char::isDigit)?.takeIf { it.isNotBlank() }?.let { "barcode:" + it }
            ?: "food:" + food.id

    private fun encodeMicros(micros: Map<String, NativeNutrient>): String {
        val root = JSONObject()
        micros.forEach { (id, nutrient) ->
            root.put(id, JSONObject().apply {
                put("label", nutrient.label)
                put("value", nutrient.valuePer100)
                put("unit", nutrient.unit)
            })
        }
        return root.toString()
    }

    private fun decodeMicros(raw: String?): Map<String, NativeNutrient> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching {
            val root = JSONObject(raw)
            buildMap {
                val keys = root.keys()
                while (keys.hasNext()) {
                    val id = keys.next()
                    val item = root.optJSONObject(id) ?: continue
                    val value = item.optDouble("value", Double.NaN)
                    if (!value.isFinite() || value < 0.0) continue
                    put(
                        id,
                        NativeNutrient(
                            id = id,
                            label = item.optString("label").ifBlank {
                                editableFoodNutrients.firstOrNull { it.id == id }?.label ?: id
                            },
                            valuePer100 = value,
                            unit = item.optString("unit").ifBlank {
                                editableFoodNutrients.firstOrNull { it.id == id }?.unit ?: "mg"
                            }
                        )
                    )
                }
            }
        }.getOrDefault(emptyMap())
    }
}

private class FoodNutritionOverrideDb(context: Context) : SQLiteOpenHelper(
    context,
    "superhuman_food_nutrition_overrides.db",
    null,
    6
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE food_nutrition_override(
                identity_key TEXT PRIMARY KEY NOT NULL,
                food_id TEXT NOT NULL,
                barcode TEXT NOT NULL,
                name TEXT NOT NULL,
                kcal REAL NOT NULL,
                kcal_known INTEGER NOT NULL DEFAULT 1,
                protein REAL NOT NULL,
                carbs REAL NOT NULL,
                fat REAL NOT NULL,
                fibre REAL NOT NULL,
                sugar REAL NOT NULL,
                saturated_fat REAL NOT NULL DEFAULT 0,
                salt REAL NOT NULL DEFAULT 0,
                sodium_mg REAL NOT NULL DEFAULT 0,
                protein_known INTEGER NOT NULL DEFAULT 1,
                carbs_known INTEGER NOT NULL DEFAULT 1,
                fat_known INTEGER NOT NULL DEFAULT 1,
                fibre_known INTEGER NOT NULL DEFAULT 1,
                sugar_known INTEGER NOT NULL DEFAULT 1,
                saturated_fat_known INTEGER NOT NULL DEFAULT 0,
                salt_known INTEGER NOT NULL DEFAULT 0,
                sodium_known INTEGER NOT NULL DEFAULT 0,
                micronutrients_json TEXT NOT NULL,
                updated_epoch_ms INTEGER NOT NULL,
                revision INTEGER NOT NULL DEFAULT 1,
                edited_fields TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX food_override_barcode_idx ON food_nutrition_override(barcode)")
        db.execSQL("CREATE INDEX food_override_food_id_idx ON food_nutrition_override(food_id)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            // Existing override fields were explicitly saved by the user, so treating them as known
            // is the least destructive migration. New edits can preserve unknown fields explicitly.
            db.execSQL("ALTER TABLE food_nutrition_override ADD COLUMN protein_known INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE food_nutrition_override ADD COLUMN carbs_known INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE food_nutrition_override ADD COLUMN fat_known INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE food_nutrition_override ADD COLUMN fibre_known INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE food_nutrition_override ADD COLUMN sugar_known INTEGER NOT NULL DEFAULT 1")
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE food_nutrition_override ADD COLUMN kcal_known INTEGER NOT NULL DEFAULT 1")
        }
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE food_nutrition_override ADD COLUMN revision INTEGER NOT NULL DEFAULT 1")
        }
        if (oldVersion < 5) {
            db.execSQL("ALTER TABLE food_nutrition_override ADD COLUMN saturated_fat REAL NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE food_nutrition_override ADD COLUMN salt REAL NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE food_nutrition_override ADD COLUMN sodium_mg REAL NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE food_nutrition_override ADD COLUMN saturated_fat_known INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE food_nutrition_override ADD COLUMN salt_known INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE food_nutrition_override ADD COLUMN sodium_known INTEGER NOT NULL DEFAULT 0")
        }
        if (oldVersion < 6) {
            db.execSQL(
                "ALTER TABLE food_nutrition_override ADD COLUMN edited_fields TEXT NOT NULL DEFAULT " +
                    "'energy_kcal|protein|carbohydrate|fat|fibre|sugars'"
            )
        }
    }
}
