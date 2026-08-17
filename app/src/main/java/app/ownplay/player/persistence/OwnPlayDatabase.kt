package app.ownplay.player.persistence

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import app.ownplay.player.persistence.live.LiveBrowseDao

@Database(
    entities = [
        PlaylistSourceEntity::class,
        ProviderCategoryEntity::class,
        ProviderChannelEntity::class,
        ChannelCustomizationEntity::class,
        HiddenEntryEntity::class,
        FavoriteEntryEntity::class,
        CustomGroupEntity::class,
        CustomGroupMembershipEntity::class,
        PlaylistRefreshStateEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class OwnPlayDatabase : RoomDatabase() {
    abstract fun playlistSourceDao(): PlaylistSourceDao
    abstract fun providerCatalogDao(): ProviderCatalogDao
    abstract fun personalizationDao(): PersonalizationDao
    abstract fun refreshStateDao(): RefreshStateDao
    abstract fun liveBrowseDao(): LiveBrowseDao

    companion object {
        const val DATABASE_NAME = "ownplay.db"

        fun create(context: Context): OwnPlayDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                OwnPlayDatabase::class.java,
                DATABASE_NAME,
            ).build()
    }
}
