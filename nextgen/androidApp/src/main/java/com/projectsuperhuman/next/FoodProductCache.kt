package com.projectsuperhuman.next

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

internal data class CachedFoodProduct(
    val barcode: String,
    val productJson: String,
    val retrievedEpochMs: Long
)

/**
 * Small persistent cache for raw branded-product evidence.
 *
 * We cache the source payload rather than a second food model. On read the payload is parsed through
 * the normal Open Food Facts adapter again, so the canonicalisation and integrity rules stay single-
 * sourced. Cache rows are only a fallback when the network is unavailable.
 */
internal object FoodProductCache {
    fun put(context: Context, barcode: String, productJson: String, retrievedEpochMs: Long) {
        val digits = barcode.filter(Char::isDigit)
        if (digits.isBlank() || productJson.isBlank()) return
        FoodProductCacheDb(context.applicationContext).use { helper ->
            val values = ContentValues().apply {
                put("barcode", digits)
                put("product_json", productJson)
                put("retrieved_epoch_ms", retrievedEpochMs)
            }
            helper.writableDatabase.insertWithOnConflict(
                "food_product_cache",
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE
            )
        }
    }

    fun get(context: Context, barcode: String): CachedFoodProduct? {
        val digits = barcode.filter(Char::isDigit)
        if (digits.isBlank()) return null
        FoodProductCacheDb(context.applicationContext).use { helper ->
            helper.readableDatabase.rawQuery(
                """
                SELECT product_json, retrieved_epoch_ms
                FROM food_product_cache
                WHERE barcode = ?
                LIMIT 1
                """.trimIndent(),
                arrayOf(digits)
            ).use { cursor ->
                if (!cursor.moveToFirst()) return null
                return CachedFoodProduct(
                    barcode = digits,
                    productJson = cursor.getString(0),
                    retrievedEpochMs = cursor.getLong(1)
                )
            }
        }
    }
}

private class FoodProductCacheDb(context: Context) : SQLiteOpenHelper(
    context,
    "superhuman_food_product_cache.db",
    null,
    1
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE food_product_cache(
                barcode TEXT PRIMARY KEY NOT NULL,
                product_json TEXT NOT NULL,
                retrieved_epoch_ms INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX food_product_cache_retrieved_idx ON food_product_cache(retrieved_epoch_ms DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
}
