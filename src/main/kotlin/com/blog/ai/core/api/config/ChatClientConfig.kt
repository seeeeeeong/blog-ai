package com.blog.ai.core.api.config

import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor
import org.springframework.ai.chat.memory.ChatMemory
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ChatClientConfig {
    companion object {
        private const val RAG_TOP_K = 5
    }

    @Bean
    fun chatClient(
        chatClientBuilder: ChatClient.Builder,
        vectorStore: VectorStore,
        chatMemory: ChatMemory,
    ): ChatClient {
        val retriever =
            VectorStoreDocumentRetriever
                .builder()
                .vectorStore(vectorStore)
                .topK(RAG_TOP_K)
                .build()

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
                Provide accurate and helpful answers based on the retrieved tech blog articles.
                Always include sources (company name, article title, URL) in your responses.
                Respond in Korean, but use technical terms as-is.
                """.trimIndent(),
            ).defaultAdvisors(ragAdvisor, memoryAdvisor)
            .build()
    }
}
