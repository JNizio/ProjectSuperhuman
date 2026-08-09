package com.projectsuperhuman.next.data

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.projectsuperhuman.next.db.SuperhumanDatabase

actual class DatabaseDriverFactory(private val context: Context) {
    actual fun createDriver(): SqlDriver =
        AndroidSqliteDriver(SuperhumanDatabase.Schema, context, "project_superhuman.db")
}
