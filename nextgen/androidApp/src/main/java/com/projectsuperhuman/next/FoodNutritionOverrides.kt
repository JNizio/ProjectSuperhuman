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
            val values = ContentValues().apply {
                put("identity_key", identityKey(food))
                put("food_id", food.id)
                put("barcode", food.barcode.orEmpty())
                put("name", food.name)
                put("kcal", food.kcal)
                put("protein", food.protein)
                put("carbs", food.carbs)
                put("fat", food.fat)
                put("fibre", food.fibre)
                put("sugar", food.sugar)
                put("micronutrients_json", encodeMicros(food.micronutrients))
                put("updated_epoch_ms", System.currentTimeMillis())
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
            SELECT kcal, protein, carbs, fat, fibre, sugar, micronutrients_json
            FROM food_nutrition_override
            WHERE identity_key = ?
            LIMIT 1
            """.trimIndent(),
            arrayOf(identityKey(food))
        ).use { cursor ->
            if (!cursor.moveToFirst()) return food
            val micros = decodeMicros(cursor.getString(6))
            val editedSource = if (food.source.contains("edited locally", ignoreCase = true)) {
                food.source
            } else {
                "${food.source} · edited locally"
            }
            return food.copy(
                kcal = cursor.getDouble(0),
                protein = cursor.getDouble(1),
                carbs = cursor.getDouble(2),
                fat = cursor.getDouble(3),
                fibre = cursor.getDouble(4),
                sugar = cursor.getDouble(5),
                micronutrients = micros,
                source = editedSource
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
    1
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
                protein REAL NOT NULL,
                carbs REAL NOT NULL,
                fat REAL NOT NULL,
                fibre REAL NOT NULL,
                sugar REAL NOT NULL,
                micronutrients_json TEXT NOT NULL,
                updated_epoch_ms INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX food_override_barcode_idx ON food_nutrition_override(barcode)")
        db.execSQL("CREATE INDEX food_override_food_id_idx ON food_nutrition_override(food_id)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
}
