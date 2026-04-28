package com.blog.ai.global.persistence

import java.sql.Timestamp
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

object JdbcTimeMapper {
    fun toOffsetDateTime(value: Any?): OffsetDateTime? =
        when (value) {
            null -> null
            is OffsetDateTime -> value
            is Instant -> value.atOffset(ZoneOffset.UTC)
            is Timestamp -> value.toInstant().atOffset(ZoneOffset.UTC)
            else -> error("unexpected TIMESTAMPTZ runtime type: ${value.javaClass}")
        }
}
