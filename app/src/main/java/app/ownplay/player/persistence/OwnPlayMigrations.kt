package app.ownplay.player.persistence

import androidx.room.migration.Migration

val MIGRATION_1_2 = Migration(1, 2) { database ->
    database.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `recent_channels` (
            `channelId` TEXT NOT NULL,
            `watchedAtEpochMillis` INTEGER NOT NULL,
            PRIMARY KEY(`channelId`),
            FOREIGN KEY(`channelId`) REFERENCES `provider_channels`(`channelId`)
                ON UPDATE NO ACTION ON DELETE CASCADE
        )
        """.trimIndent(),
    )
    database.execSQL(
        "CREATE INDEX IF NOT EXISTS `index_recent_channels_watchedAtEpochMillis` " +
            "ON `recent_channels` (`watchedAtEpochMillis`)",
    )
}
