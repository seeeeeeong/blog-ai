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
    var items: String? = null,

    @Column(name = "fetched_at")
    var fetchedAt: OffsetDateTime? = OffsetDateTime.now(),
)
