package app.ownplay.player.live

import app.ownplay.player.persistence.live.LiveBrowseDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

data class LiveCustomGroup(
    val groupId: String,
    val name: String,
    val groupOrder: Long,
)

data class LiveCatalogSnapshot(
    val categories: List<LiveCategory>,
    val channels: List<app.ownplay.player.persistence.live.LiveChannelRecord>,
    val customGroups: List<LiveCustomGroup> = emptyList(),
    val customGroupIdsByChannelId: Map<String, Set<String>> = emptyMap(),
)

class LiveCatalogRepository(
    private val dao: LiveBrowseDao,
) {
    fun observe(sourceId: String): Flow<LiveCatalogSnapshot> = combine(
        dao.observeCategories(sourceId),
        dao.observeChannels(sourceId),
        dao.observeCustomGroups(sourceId),
        dao.observeGroupMemberships(sourceId),
    ) { categories, channels, customGroups, memberships ->
        LiveCatalogSnapshot(
            categories = categories.map { category ->
                LiveCategory(
                    providerCategoryKey = category.providerCategoryKey,
                    name = category.name,
                    providerOrder = category.providerOrder,
                )
            },
            channels = channels,
            customGroups = customGroups.map { group ->
                LiveCustomGroup(
                    groupId = group.groupId,
                    name = group.name,
                    groupOrder = group.groupOrder,
                )
            },
            customGroupIdsByChannelId = memberships
                .groupBy { membership -> membership.channelId }
                .mapValues { (_, channelMemberships) ->
                    channelMemberships.mapTo(linkedSetOf()) { membership -> membership.groupId }
                },
        )
    }
}
