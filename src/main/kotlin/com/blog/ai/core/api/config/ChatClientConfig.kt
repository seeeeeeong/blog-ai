package com.blog.ai.core.api.config

import com.blog.ai.core.domain.chat.BlogArticleDocumentRetriever
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor
import org.springframework.ai.chat.memory.ChatMemory
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ChatClientConfig {
    @Bean
    fun chatClient(
        chatClientBuilder: ChatClient.Builder,
        retriever: BlogArticleDocumentRetriever,
        chatMemory: ChatMemory,
    ): ChatClient {
        val ragAdvisor =
            RetrievalAugmentationAdvisor
                .builder()
                .documentRetriever(retriever)
                .build()

        val memoryAdvisor =
            MessageChatMemoryAdvisor
                .builder(chatMemory)
                .build()

        return chatClientBuilder
            .defaultSystem(
                """
                You are an AI assistant specialized in Korean corporate tech blogs.
                Only answer from the retrieved tech blog articles provided as context.
                If the context does not contain relevant information, say so honestly
                instead of guessing. Always cite sources using markdown links in the
                format: [company - title](url). Respond in Korean but keep technical
                terms as-is.
                """.trimIndent(),
            ).defaultAdvisors(ragAdvisor, memoryAdvisor)
            .build()
    }
}
