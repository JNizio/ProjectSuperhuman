package com.projectsuperhuman.next.data

import app.cash.sqldelight.db.SqlDriver
import com.projectsuperhuman.next.db.SuperhumanDatabase

expect class DatabaseDriverFactory {
    fun createDriver(): SqlDriver
}

fun createSuperhumanDatabase(factory: DatabaseDriverFactory): SuperhumanDatabase =
    SuperhumanDatabase(factory.createDriver())
