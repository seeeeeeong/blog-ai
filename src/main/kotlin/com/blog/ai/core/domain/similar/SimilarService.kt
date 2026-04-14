package com.blog.ai.core.domain.similar

import com.blog.ai.config.properties.SimilarProperties
import com.blog.ai.storage.article.ArticleRepository
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service

data class SimilarArticle(
    val id: Long,
    val title: String,
    val url: String,
    val company: String,
    val score: Double,
)

@Service
class SimilarService(
    private val articleRepository: ArticleRepository,
    private val embeddingModel: EmbeddingModel,
    private val similarProperties: SimilarProperties,
) {

    @Cacheable(value = ["similar"], key = "#vector")
    fun findSimilar(vector: String): List<SimilarArticle> {
        val rows = articleRepository.findSimilarByVector(vector, similarProperties.topK)
        return rows.map { row ->
            SimilarArticle(
                id = (row[0] as Number).toLong(),
                title = row[1] as String,
                url = row[2] as String,
                company = row[3] as String,
                score = (row[4] as Number).toDouble(),
            )
        }
    }

    fun findSimilarByText(text: String): List<SimilarArticle> {
        val embedding = embeddingModel.embed(text)
        val vector = embedding.joinToString(",", "[", "]")
        return findSimilar(vector)
    }
}
