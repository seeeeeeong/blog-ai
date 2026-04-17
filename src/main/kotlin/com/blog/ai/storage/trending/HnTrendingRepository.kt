package com.blog.ai.storage.trending

import org.springframework.data.jpa.repository.JpaRepository

interface HnTrendingRepository : JpaRepository<HnTrendingEntity, Int>
