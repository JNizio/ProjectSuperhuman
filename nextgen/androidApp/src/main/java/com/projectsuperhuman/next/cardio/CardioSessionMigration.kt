package com.projectsuperhuman.next

internal data class CardioSessionMigrationResult(
    val session: CardioSession,
    val fromVersion: Int,
    val toVersion: Int,
    val migrated: Boolean,
    val futureSchema: Boolean = false
)

internal object CardioSessionSchemaMigration {
    fun migrate(session: CardioSession): CardioSessionMigrationResult {
        val from = session.schemaVersion
        if (from > CARDIO_SESSION_SCHEMA_VERSION) {
            return CardioSessionMigrationResult(
                session = session,
                fromVersion = from,
                toVersion = from,
                migrated = false,
                futureSchema = true
            )
        }
        if (from == CARDIO_SESSION_SCHEMA_VERSION) {
            return CardioSessionMigrationResult(
                session = session,
                fromVersion = from,
                toVersion = from,
                migrated = false
            )
        }

        val upgraded = session.copy(
            schemaVersion = CARDIO_SESSION_SCHEMA_VERSION,
            extensions = session.extensions + mapOf(
                "originalCardioSchemaVersion" to from.toString(),
                "schemaMigration" to ("v" + from + "-to-v" + CARDIO_SESSION_SCHEMA_VERSION)
            )
        )
        return CardioSessionMigrationResult(
            session = upgraded,
            fromVersion = from,
            toVersion = CARDIO_SESSION_SCHEMA_VERSION,
            migrated = true
        )
    }
}
