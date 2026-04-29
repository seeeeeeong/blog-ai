package com.blog.ai.global.config

import com.blog.ai.chat.application.retrieval.ArticleRetriever
import com.blog.ai.global.text.PromptLoader
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor
import org.springframework.ai.chat.memory.ChatMemory
import org.springframework.ai.chat.prompt.PromptTemplate
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.Resource

@Configuration
class AiConfig {
    @Bean
    fun chatClient(
        chatClientBuilder: ChatClient.Builder,
        retriever: ArticleRetriever,
        chatMemory: ChatMemory,
        @Value("classpath:prompts/chat/system.st")
        systemPromptResource: Resource,
        @Value("classpath:prompts/chat/rag-context.st")
        ragContextPromptResource: Resource,
        @Value("classpath:prompts/chat/empty-context.st")
        emptyContextPromptResource: Resource,
    ): ChatClient {
        val systemPrompt = PromptLoader.load(systemPromptResource)
        val ragContextPrompt = PromptLoader.load(ragContextPromptResource)
        val emptyContextPrompt = PromptLoader.load(emptyContextPromptResource)

        val ragAdvisor =
            RetrievalAugmentationAdvisor
                .builder()
                .documentRetriever(retriever)
                .queryAugmenter(
                    ContextualQueryAugmenter
                        .builder()
                        .allowEmptyContext(true)
                        .promptTemplate(PromptTemplate(ragContextPrompt))
                        .emptyContextPromptTemplate(PromptTemplate(emptyContextPrompt))
                        .build(),
                ).build()

        val memoryAdvisor =
            MessageChatMemoryAdvisor
                .builder(chatMemory)
                .build()

        return chatClientBuilder
            .defaultSystem(systemPrompt)
            .defaultAdvisors(ragAdvisor, memoryAdvisor)
            .build()
    }
}
