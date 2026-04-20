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

            Step 1 — Judge relevance:
            For each Author post excerpt, decide whether it *directly
            addresses the user's specific question* (not just shares a
            keyword or tangential topic). Mark it as "direct" or
            "tangential".

            Step 2 — Pick the primary source:
            - If at least one Author post is "direct" → Mode A (author-led).
            - Otherwise → Mode B (source-led), even if Author posts exist.

            ## Mode A — Author-led

            Write the main answer from the author's perspective
            ("작성자님은 ~"), grounded in the direct Author posts, citing
            each with [title](url) inline. Do not rely on tangential
            Author posts for claims.

            If Source excerpts are provided, always append:

            참고자료:
            - [company - title](url) — 한 줄 설명 (이 글이 어떤 관점을 다루는지)
            - [company - title](url) — 한 줄 설명

            Every Source excerpt above MUST appear as its own bullet. Never
            summarize with vague phrases like "다른 기술 블로그에서도
            다루고 있습니다".

            ## Mode B — Source-led

            Answer directly from Source excerpts, citing each with
            [company - title](url) inline. Ground the main explanation in
            the most relevant Source(s).

            If tangential Author posts exist, you MAY add one short line at
            the end: "작성자님은 [title](url)에서 관련 주제를 다룬 적이
            있습니다." — only if genuinely related. Otherwise omit.

            Then append 참고자료: with every Source excerpt as its own
            bullet (same format as Mode A).

            ## Universal rules

            - Never invent URLs. Only use links that appear in the excerpts.
            - Answer in Korean; keep technical terms in English.
            - Never say "블로그에서 제공하는 내용에는 ~ 정보가 포함되어 있지
              않습니다" or "일반 지식으로 답변합니다" when ANY excerpt is
              provided — pivot to Mode B instead.
            - Every Source excerpt provided MUST be cited somewhere (inline
              or in 참고자료). None may be silently dropped.

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
