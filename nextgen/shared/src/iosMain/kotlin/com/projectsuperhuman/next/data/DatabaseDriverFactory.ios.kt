package com.projectsuperhuman.next.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.projectsuperhuman.next.db.SuperhumanDatabase

actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver =
        NativeSqliteDriver(SuperhumanDatabase.Schema, "project_superhuman.db")
}
