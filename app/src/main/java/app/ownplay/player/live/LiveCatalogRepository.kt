package app.ownplay.player.live

import app.ownplay.player.persistence.live.LiveBrowseDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

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
    val hiddenCategoryKeys: Set<String> = emptySet(),
)

class LiveCatalogRepository(
    private val dao: LiveBrowseDao,
    private val observeHiddenCategoryKeys: (String) -> Flow<Set<String>> = {
        flowOf(emptySet())
    },
) {
    fun observe(sourceId: String): Flow<LiveCatalogSnapshot> = combine(
        dao.observeCategories(sourceId),
        dao.observeChannels(sourceId),
        dao.observeCustomGroups(),
        dao.observeGroupMemberships(sourceId),
        observeHiddenCategoryKeys(sourceId),
    ) { categories, channels, customGroups, memberships, hiddenCategoryKeys ->
        LiveCatalogSnapshot(
            categories = categories.map { category ->
                LiveCategory(
                    providerCategoryKey = category.providerCategoryKey,
                    name = category.name,
                    providerOrder = category.providerOrder,
                    isHidden = category.providerCategoryKey in hiddenCategoryKeys,
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
            hiddenCategoryKeys = hiddenCategoryKeys,
        )
    }
}
