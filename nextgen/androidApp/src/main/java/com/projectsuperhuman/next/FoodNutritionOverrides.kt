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

    fun save(context: Context, food: NativeFood) {
        attach(context)
        FoodNutritionOverrideDb(context.applicationContext).use { helper ->
            val key = identityKey(food)
            val revision = helper.readableDatabase.rawQuery(
                "SELECT revision FROM food_nutrition_override WHERE identity_key = ? LIMIT 1",
                arrayOf(key)
            ).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) + 1 else 1 }
            val values = ContentValues().apply {
                put("identity_key", key)
                put("food_id", food.id)
                put("barcode", food.barcode.orEmpty())
                put("name", food.name)
                put("kcal", food.kcal)
                put("kcal_known", if (food.kcalKnown) 1 else 0)
                put("protein", food.protein)
                put("carbs", food.carbs)
                put("fat", food.fat)
                put("fibre", food.fibre)
                put("sugar", food.sugar)
                put("saturated_fat", food.saturatedFat)
                put("salt", food.salt)
                put("sodium_mg", food.sodiumMg)
                put("protein_known", if (food.proteinKnown) 1 else 0)
                put("carbs_known", if (food.carbsKnown) 1 else 0)
                put("fat_known", if (food.fatKnown) 1 else 0)
                put("fibre_known", if (food.fibreKnown) 1 else 0)
                put("sugar_known", if (food.sugarKnown) 1 else 0)
                put("saturated_fat_known", if (food.saturatedFatKnown) 1 else 0)
                put("salt_known", if (food.saltKnown) 1 else 0)
                put("sodium_known", if (food.sodiumKnown) 1 else 0)
                put("micronutrients_json", encodeMicros(food.micronutrients))
                put("updated_epoch_ms", System.currentTimeMillis())
                put("revision", revision)
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
                   saturated_fat_known, salt_known, sodium_known
            FROM food_nutrition_override
            WHERE identity_key = ?
            LIMIT 1
            """.trimIndent(),
            arrayOf(identityKey(food))
        ).use { cursor ->
            if (!cursor.moveToFirst()) return food
            val correctionEpochMs = cursor.getLong(13)
            val revision = cursor.getInt(14)
            val micros = decodeMicros(cursor.getString(12)).mapValues { (_, nutrient) ->
                nutrient.copy(
                    evidenceKind = NutrientEvidenceKind.USER_ENTERED,
                    source = "User correction",
                    sourceRecordId = identityKey(food)
                )
            }
            val editedSource = if (food.source.contains("edited locally", ignoreCase = true)) {
                food.source
            } else {
                "${food.source} · edited locally"
            }
            return food.copy(
                kcal = cursor.getDouble(0),
                kcalKnown = cursor.getInt(6) != 0,
                protein = cursor.getDouble(1),
                carbs = cursor.getDouble(2),
                fat = cursor.getDouble(3),
                fibre = cursor.getDouble(4),
                sugar = cursor.getDouble(5),
                proteinKnown = cursor.getInt(7) != 0,
                carbsKnown = cursor.getInt(8) != 0,
                fatKnown = cursor.getInt(9) != 0,
                fibreKnown = cursor.getInt(10) != 0,
                sugarKnown = cursor.getInt(11) != 0,
                saturatedFat = cursor.getDouble(15),
                salt = cursor.getDouble(16),
                sodiumMg = cursor.getDouble(17),
                saturatedFatKnown = cursor.getInt(18) != 0,
                saltKnown = cursor.getInt(19) != 0,
                sodiumKnown = cursor.getInt(20) != 0,
                micronutrients = micros,
                nutritionIntegrityWarning = null,
                nutritionApproximate = false,
                source = editedSource,
                sourceType = FoodDataSourceType.USER_CORRECTED,
                sourceRevision = "user-correction-v" + revision + "@" + correctionEpochMs,
                verificationState = FoodVerificationState.USER_CORRECTED,
                confidence = FoodDataConfidence.HIGH,
                energyEvidence = if (cursor.getInt(6) != 0) EnergyEvidenceKind.USER_ENTERED else food.energyEvidence,
                nutrientEvidence = buildMap {
                    if (cursor.getInt(6) != 0) put("energy_kcal", NutrientEvidenceKind.USER_ENTERED)
                    if (cursor.getInt(7) != 0) put("protein", NutrientEvidenceKind.USER_ENTERED)
                    if (cursor.getInt(8) != 0) put("carbohydrate", NutrientEvidenceKind.USER_ENTERED)
                    if (cursor.getInt(9) != 0) put("fat", NutrientEvidenceKind.USER_ENTERED)
                    if (cursor.getInt(10) != 0) put("fibre", NutrientEvidenceKind.USER_ENTERED)
                    if (cursor.getInt(11) != 0) put("sugars", NutrientEvidenceKind.USER_ENTERED)
                    if (cursor.getInt(18) != 0) put("saturated_fat", NutrientEvidenceKind.USER_ENTERED)
                    if (cursor.getInt(19) != 0) put("salt", NutrientEvidenceKind.USER_ENTERED)
                    if (cursor.getInt(20) != 0) put("sodium", NutrientEvidenceKind.USER_ENTERED)
                },
                sourceWarnings = emptyList()
            )
        }
    }

    private fun identityKey(food: NativeFood): String =
        food.barcode?.filter(Char::isDigit)?.takeIf { it.isNotBlank() }?.let { "barcode:$it" }
            ?: "food:${food.id}"

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
    5
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
                revision INTEGER NOT NULL DEFAULT 1
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
    }
}
