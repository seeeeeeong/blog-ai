package com.blog.ai.storage.article

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "article_chunks")
class ArticleChunkEntity(
    @Column(name = "article_id", nullable = false)
    val articleId: Long = 0L,

    @Column(name = "chunk_index", nullable = false)
    val chunkIndex: Int = 0,

    @Column(nullable = false, columnDefinition = "TEXT")
    val content: String = "",

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,
)
