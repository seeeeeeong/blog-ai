package com.blog.ai.blog.entity

import com.blog.ai.global.jpa.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

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

    companion object {
        fun create(
            name: String,
            company: String,
            rssUrl: String,
            homeUrl: String,
            active: Boolean = true,
        ): BlogEntity {
            require(name.isNotBlank()) { "Blog name must not be blank" }
            require(company.isNotBlank()) { "Blog company must not be blank" }
            require(rssUrl.isNotBlank()) { "Blog rssUrl must not be blank" }
            require(homeUrl.isNotBlank()) { "Blog homeUrl must not be blank" }
            return BlogEntity(
                name = name,
                company = company,
                rssUrl = rssUrl,
                homeUrl = homeUrl,
                active = active,
            )
        }
    }
}
