package com.blog.ai.config

import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor
import org.springframework.ai.chat.memory.ChatMemory
import org.springframework.ai.chat.memory.InMemoryChatMemory
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ChatClientConfig {

    @Bean
    fun chatMemory(): ChatMemory {
        return InMemoryChatMemory()
    }

    @Bean
    fun chatClient(
        chatClientBuilder: ChatClient.Builder,
        vectorStore: VectorStore,
        chatMemory: ChatMemory,
    ): ChatClient {
        val retriever = VectorStoreDocumentRetriever.builder()
            .vectorStore(vectorStore)
            .topK(5)
            .build()

        val ragAdvisor = RetrievalAugmentationAdvisor.builder()
            .documentRetriever(retriever)
            .build()

        val memoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory)
            .build()

        return chatClientBuilder
            .defaultSystem(
                """
                당신은 한국 기업 기술블로그 전문 AI 어시스턴트입니다.
                검색된 기술블로그 글을 기반으로 정확하고 도움이 되는 답변을 제공합니다.
                답변 시 출처(기업명, 글 제목, URL)를 반드시 포함해주세요.
                한국어로 답변하되, 기술 용어는 원문 그대로 사용하세요.
                """.trimIndent(),
            )
            .defaultAdvisors(ragAdvisor, memoryAdvisor)
            .build()
    }
}
