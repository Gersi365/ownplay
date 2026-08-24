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

val MIGRATION_2_3 = Migration(2, 3) { database ->
    database.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `provider_vod_categories` (
            `categoryId` TEXT NOT NULL,
            `sourceId` TEXT NOT NULL,
            `providerCategoryKey` TEXT NOT NULL,
            `name` TEXT NOT NULL,
            `parentProviderKey` TEXT,
            `providerOrder` INTEGER NOT NULL,
            `lastSeenGeneration` INTEGER NOT NULL,
            PRIMARY KEY(`categoryId`),
            FOREIGN KEY(`sourceId`) REFERENCES `playlist_sources`(`sourceId`)
                ON UPDATE NO ACTION ON DELETE CASCADE
        )
        """.trimIndent(),
    )
    database.execSQL(
        "CREATE INDEX IF NOT EXISTS `index_provider_vod_categories_sourceId` " +
            "ON `provider_vod_categories` (`sourceId`)",
    )
    database.execSQL(
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_provider_vod_categories_sourceId_providerCategoryKey` " +
            "ON `provider_vod_categories` (`sourceId`, `providerCategoryKey`)",
    )
    database.execSQL(
        "CREATE INDEX IF NOT EXISTS `index_provider_vod_categories_sourceId_providerOrder` " +
            "ON `provider_vod_categories` (`sourceId`, `providerOrder`)",
    )

    database.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `provider_movies` (
            `movieId` TEXT NOT NULL,
            `sourceId` TEXT NOT NULL,
            `providerStreamId` TEXT NOT NULL,
            `providerCategoryKey` TEXT,
            `providerName` TEXT NOT NULL,
            `posterRef` TEXT,
            `containerExtension` TEXT,
            `providerRating` REAL,
            `addedAtEpochSeconds` INTEGER,
            `providerOrder` INTEGER NOT NULL,
            `lastSeenGeneration` INTEGER NOT NULL,
            PRIMARY KEY(`movieId`),
            FOREIGN KEY(`sourceId`) REFERENCES `playlist_sources`(`sourceId`)
                ON UPDATE NO ACTION ON DELETE CASCADE
        )
        """.trimIndent(),
    )
    database.execSQL(
        "CREATE INDEX IF NOT EXISTS `index_provider_movies_sourceId` " +
            "ON `provider_movies` (`sourceId`)",
    )
    database.execSQL(
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_provider_movies_sourceId_providerStreamId` " +
            "ON `provider_movies` (`sourceId`, `providerStreamId`)",
    )
    database.execSQL(
        "CREATE INDEX IF NOT EXISTS `index_provider_movies_sourceId_providerCategoryKey` " +
            "ON `provider_movies` (`sourceId`, `providerCategoryKey`)",
    )
    database.execSQL(
        "CREATE INDEX IF NOT EXISTS `index_provider_movies_sourceId_providerOrder` " +
            "ON `provider_movies` (`sourceId`, `providerOrder`)",
    )

    database.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `media_favorites` (
            `sourceId` TEXT NOT NULL,
            `mediaKind` TEXT NOT NULL,
            `contentId` TEXT NOT NULL,
            `addedAtEpochMillis` INTEGER NOT NULL,
            PRIMARY KEY(`sourceId`, `mediaKind`, `contentId`),
            FOREIGN KEY(`sourceId`) REFERENCES `playlist_sources`(`sourceId`)
                ON UPDATE NO ACTION ON DELETE CASCADE
        )
        """.trimIndent(),
    )
    database.execSQL(
        "CREATE INDEX IF NOT EXISTS `index_media_favorites_sourceId` " +
            "ON `media_favorites` (`sourceId`)",
    )
    database.execSQL(
        "CREATE INDEX IF NOT EXISTS `index_media_favorites_mediaKind_addedAtEpochMillis` " +
            "ON `media_favorites` (`mediaKind`, `addedAtEpochMillis`)",
    )

    database.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `playback_progress` (
            `sourceId` TEXT NOT NULL,
            `mediaKind` TEXT NOT NULL,
            `contentId` TEXT NOT NULL,
            `positionMs` INTEGER NOT NULL,
            `durationMs` INTEGER,
            `completed` INTEGER NOT NULL,
            `updatedAtEpochMillis` INTEGER NOT NULL,
            PRIMARY KEY(`sourceId`, `mediaKind`, `contentId`),
            FOREIGN KEY(`sourceId`) REFERENCES `playlist_sources`(`sourceId`)
                ON UPDATE NO ACTION ON DELETE CASCADE
        )
        """.trimIndent(),
    )
    database.execSQL(
        "CREATE INDEX IF NOT EXISTS `index_playback_progress_sourceId` " +
            "ON `playback_progress` (`sourceId`)",
    )
    database.execSQL(
        "CREATE INDEX IF NOT EXISTS `index_playback_progress_mediaKind_updatedAtEpochMillis` " +
            "ON `playback_progress` (`mediaKind`, `updatedAtEpochMillis`)",
    )
}
