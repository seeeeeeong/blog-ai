package com.blog.ai.storage.post

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "blog_post_chunks")
class BlogPostChunkEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(name = "post_id", nullable = false)
    val postId: Long,
    @Column(name = "chunk_index", nullable = false)
    val chunkIndex: Int,
    @Column(nullable = false, columnDefinition = "TEXT")
    val content: String,
)
