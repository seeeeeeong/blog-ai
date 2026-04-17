package com.blog.ai.storage.trending

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.Hibernate
import java.time.OffsetDateTime

@Entity
@Table(name = "hn_trending")
class HnTrendingEntity(

    @Id
    val id: Int = 1,

    items: String? = null,
    fetchedAt: OffsetDateTime? = OffsetDateTime.now(),

) {

    @Column(columnDefinition = "JSONB")
    var items: String? = items
        protected set

    @Column(name = "fetched_at")
    var fetchedAt: OffsetDateTime? = fetchedAt
        protected set

    fun updateItems(itemsJson: String) {
        this.items = itemsJson
        this.fetchedAt = OffsetDateTime.now()
    }

    companion object {
        fun create(): HnTrendingEntity = HnTrendingEntity()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) return false
        other as HnTrendingEntity
        return id == other.id
    }

    override fun hashCode(): Int = Hibernate.getClass(this).hashCode()
}
