package com.blog.ai.core.api.config

import com.blog.ai.core.domain.chat.BlogArticleDocumentRetriever
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor
import org.springframework.ai.chat.memory.ChatMemory
import org.springframework.ai.chat.prompt.PromptTemplate
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter
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
                .queryAugmenter(
                    ContextualQueryAugmenter
                        .builder()
                        .allowEmptyContext(true)
                        .promptTemplate(PromptTemplate(RAG_PROMPT_TEMPLATE))
                        .emptyContextPromptTemplate(PromptTemplate(EMPTY_CONTEXT_PROMPT_TEMPLATE))
                        .build(),
                ).build()

        val memoryAdvisor =
            MessageChatMemoryAdvisor
                .builder(chatMemory)
                .build()

        return chatClientBuilder
            .defaultSystem(SYSTEM_PROMPT)
            .defaultAdvisors(ragAdvisor, memoryAdvisor)
            .build()
    }

    companion object {
        private val SYSTEM_PROMPT =
            """
            You are an AI assistant specialized in Korean corporate tech blogs.
            When retrieved blog excerpts are provided, treat them as the primary
            source and cite every claim drawn from them using markdown links in
            the format [company - title](url). If the excerpts are insufficient
            to fully answer, you may supplement with general knowledge, but say
            so plainly (e.g., "블로그에는 관련 내용이 없어 일반 지식으로 답변합니다").
            Respond in Korean. Keep technical terms as-is (English).
            """.trimIndent()

        private val RAG_PROMPT_TEMPLATE =
            """
            The following are tech blog excerpts retrieved for the user's question.
            Each excerpt starts with a "Source: [company - title](url)" link that
            MUST be used verbatim when citing that excerpt.

            ---------------------
            {context}
            ---------------------

            Instructions:
            1. Prefer the retrieved excerpts. Cite every claim from them with the
               provided Source link.
            2. If the excerpts do not cover the question, you may add general
               knowledge but clearly note which parts are not grounded.
            3. Do not invent URLs — only use Source links that appear above.
            4. Answer in Korean; keep technical terms in English.

            Query: {query}

            Answer:
            """.trimIndent()

        private val EMPTY_CONTEXT_PROMPT_TEMPLATE =
            """
            No tech blog articles were retrieved for this question. Answer from
            general knowledge and clearly note at the start that the response is
            not grounded in the blog corpus (e.g., "블로그에서 관련 글을 찾지 못해
            일반 지식으로 답변합니다"). Keep technical terms in English; respond
            in Korean.

            Query: {query}

            Answer:
            """.trimIndent()
    }
}
