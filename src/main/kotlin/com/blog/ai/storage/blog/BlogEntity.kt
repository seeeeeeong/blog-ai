package com.blog.ai.storage.blog

import com.blog.ai.storage.common.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.Hibernate

@Entity
@Table(name = "blogs")
class BlogEntity(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false, length = 100)
    val name: String,

    @Column(nullable = false, length = 100)
    val company: String,

    @Column(name = "rss_url", nullable = false, length = 500)
    val rssUrl: String,

    @Column(name = "home_url", nullable = false, length = 500)
    val homeUrl: String,

    active: Boolean = true,

) : BaseTimeEntity() {

    @Column(nullable = false)
    var active: Boolean = active
        protected set

    fun deactivate() {
        this.active = false
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) return false
        other as BlogEntity
        return id != null && id == other.id
    }

    override fun hashCode(): Int = Hibernate.getClass(this).hashCode()
}
