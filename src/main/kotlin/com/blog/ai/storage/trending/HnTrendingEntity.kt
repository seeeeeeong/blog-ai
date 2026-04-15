package com.blog.ai.storage.trending

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.OffsetDateTime

@Entity
@Table(name = "hn_trending")
class HnTrendingEntity(
    @Id
    val id: Int = 1,

    @Column(columnDefinition = "JSONB")
    private var items: String? = null,

    @Column(name = "fetched_at")
    private var fetchedAt: OffsetDateTime? = OffsetDateTime.now(),
) {

    companion object {
        fun create(): HnTrendingEntity = HnTrendingEntity()
    }

    fun updateItems(itemsJson: String) {
        items = itemsJson
        fetchedAt = OffsetDateTime.now()
    }

    fun getItemsJson(): String? = items
}
