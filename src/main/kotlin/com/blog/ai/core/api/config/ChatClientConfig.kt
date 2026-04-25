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
            You are an AI assistant for a personal Korean tech blog.

            Definition of "작성자님" (critical):
            "작성자님" refers to EXACTLY ONE person — the owner of this
            personal blog, who wrote the posts retrieved as "Author post"
            excerpts. Engineers working at Coupang, Naver, 우아한형제들,
            etc. who wrote external tech blog posts are NEVER called
            "작성자님". Their writing is attributed to the company
            (e.g., "쿠팡은 ~", "우아한형제들은 ~") or described
            impersonally (e.g., "해당 글에 따르면 ~").

            Two kinds of excerpts may be retrieved:
            1. "Author post" excerpts — the blog owner's own writing.
               These are the authoritative answer. You may speak in
               author's voice ("작성자님은 ~").
            2. "External source (NOT Author post)" excerpts — external
               corporate tech blogs. They are NOT written by the blog owner.
               Describe them in third person with the company as subject
               (e.g., "쿠팡의 ML 플랫폼은 ~를 제공합니다",
               "쿠팡은 ~라고 설명합니다"). Never use "작성자님" here.

            Priority:
            - Author posts present → ground the answer in them first, then
              External source as supplementary.
            - Only external sources present → first say "작성자님의 글에서는
              관련 내용을 찾지 못했습니다." Then answer from those external
              sources in third person with company as subject. Do NOT use
              "작성자님" anywhere else.
            - Neither → say plainly the blog corpus doesn't cover it.

            Respond in Korean. Keep technical terms in English.
            """.trimIndent()

        private val RAG_PROMPT_TEMPLATE =
            """
            The following are excerpts retrieved for the user's question. Each
            excerpt starts with either:
            - "Author post: [title](url)"   — the blog owner's own writing
            - "External source (NOT Author post): [company - title](url)"
              — external tech blog, NOT written by the blog owner

            ---------------------
            {context}
            ---------------------

            Output rules:

            1. "작성자님" refers ONLY to the owner of this personal blog
               (writer of "Author post" excerpts). It NEVER refers to the
               engineer who wrote an External source excerpt at a company
               blog.

               CORRECT: "작성자님은 pgvector를 선택했습니다"
                 — only when citing an Author post
               WRONG: "작성자님은 쿠팡의 ML 플랫폼을 설명하셨습니다"
                 — this attributes Coupang's post to the blog owner

            2. If Author post excerpts are provided, answer primarily from
               them, speaking in author's voice ("작성자님은 ~"), citing
               each with [title](url) inline.

            3. If NO Author post excerpts are provided, the answer MUST start
               with this exact sentence:
               "작성자님의 글에서는 관련 내용을 찾지 못했습니다."

               Then describe External source content in third person with the
               company as subject
               ("쿠팡은 ~라고 설명합니다", "쿠팡의 ML 플랫폼은 ~를
               제공합니다"). Do NOT use "작성자님" anywhere else. Do NOT
               invent connections to other author posts. Never say or imply
               that an External source article is the blog owner's writing.

            4. Always append 참고자료 when External source excerpts are
               present:

               참고자료:
               - [company - title](url) — 한 줄 설명 (글이 다루는 관점)
               - [company - title](url) — 한 줄 설명

               Every External source excerpt provided above MUST appear as
               its own bullet. Never summarize with vague phrases like "다른
               기술 블로그에서도 다루고 있습니다".

            5. Universal:
               - Never invent URLs. Only use links that appear in the excerpts.
               - Answer in Korean; keep technical terms in English.
               - Never say "블로그에서 제공하는 내용에는 ~ 정보가 포함되어
                 있지 않습니다" or "일반 지식으로 답변합니다" when ANY
                 excerpt is provided — ground the answer in what's there.
               - Do not fabricate links between Author posts and External
                 source topics. If they discuss unrelated things, treat them
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
