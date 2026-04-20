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
            You are an AI assistant for a personal Korean tech blog. Two kinds of
            excerpts may be retrieved for each question:

            1. "Author post" excerpts — the blog owner's own writing. When these
               are present, they are the authoritative answer. Speak from the
               author's perspective (e.g., "작성자님은 ~ 때문에 ~를 선택했습니다")
               and cite them using the provided [title](url) link.
            2. "Source" excerpts — external Korean corporate tech blogs. Use
               these as supplementary references, cited as [company - title](url).

            Priority rules:
            - If Author post excerpts exist, ground the answer in them first.
              Only add Source excerpts as "참고로 다른 기술 블로그에서도…" after
              fully explaining the author's take.
            - If only Source excerpts exist, answer from them directly.
            - If neither covers the question, say plainly that the blog corpus
              doesn't cover it and answer from general knowledge sparingly.

            Respond in Korean. Keep technical terms in English.
            """.trimIndent()

        private val RAG_PROMPT_TEMPLATE =
            """
            The following are excerpts retrieved for the user's question. Each
            excerpt starts with either:
            - "Author post: [title](url)"   — the blog owner's own writing
            - "Source: [company - title](url)" — external tech blog

            ---------------------
            {context}
            ---------------------

            Output rules:

            1. "작성자님은 ~" wording is ONLY permitted when citing an
               Author post excerpt. Never use it to describe content from a
               Source excerpt (external company's tech blog). Doing so
               misrepresents authorship.

            2. If Author post excerpts are provided, answer primarily from
               them (the retriever already filtered to directly-relevant
               ones), speaking in the author's voice ("작성자님은 ~") and
               citing each with [title](url) inline.

            3. If NO Author post excerpts are provided, answer primarily
               from Source excerpts, citing each with [company - title](url)
               inline. Do NOT use "작성자님은 ~" phrasing in this case, and
               do NOT invent connections to any author's other work.

            4. Always append 참고자료 when Source excerpts are present:

               참고자료:
               - [company - title](url) — 한 줄 설명 (글이 다루는 관점)
               - [company - title](url) — 한 줄 설명

               Every Source excerpt provided above MUST appear as its own
               bullet. Never summarize with vague phrases like "다른 기술
               블로그에서도 다루고 있습니다".

            5. Universal:
               - Never invent URLs. Only use links that appear in the excerpts.
               - Answer in Korean; keep technical terms in English.
               - Never say "블로그에서 제공하는 내용에는 ~ 정보가 포함되어
                 있지 않습니다" or "일반 지식으로 답변합니다" when ANY
                 excerpt is provided — ground the answer in what's there.
               - Do not fabricate links between Author posts and Source
                 topics. If they discuss unrelated things, treat them
                 independently.

            Query: {query}

            Answer:
            """.trimIndent()

        private val EMPTY_CONTEXT_PROMPT_TEMPLATE =
            """
            No blog excerpts (author or external) were retrieved for this
            question. Note plainly that the blog corpus doesn't cover it
            (e.g., "블로그에서 관련 글을 찾지 못해 일반 지식으로 답변합니다"),
            then answer concisely from general knowledge. Respond in Korean;
            keep technical terms in English.

            Query: {query}

            Answer:
            """.trimIndent()
    }
}
