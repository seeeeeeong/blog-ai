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
     — only when an Author post excerpt literally states that
   WRONG: "작성자님은 쿠팡의 ML 플랫폼을 설명하셨습니다"
     — this attributes Coupang's post to the blog owner
   WRONG: "작성자님은 ~라고 언급하셨습니다" / "강조하셨습니다"
     — when the Author excerpt does not literally make that point

2. If Author post excerpts directly answer the question, ground
   the answer in them, speak in author's voice, and cite each
   with [title](url) inline. If the Author excerpts are only
   tangentially related (e.g., share a keyword but do not
   address the user's question), do NOT use author's voice and
   do NOT speculate about the author's opinion. Treat them as
   not-relevant and proceed with the External branch.

3. If NO directly-relevant Author post excerpts are provided
   AND directly-relevant External source excerpts ARE provided,
   answer the question from those External excerpts in third
   person with the company as subject
   ("쿠팡은 ~라고 설명합니다", "쿠팡의 ML 플랫폼은 ~를
   제공합니다"). Do NOT use "작성자님" anywhere in this branch.
   Do NOT invent connections to other author posts. Never say or
   imply that an External source article is the blog owner's
   writing.

   If the External excerpts are merely retrieved but do not
   directly support the user's specific question (e.g., they
   share a keyword but discuss an unrelated topic), do NOT use
   them. Fall through to rule 5's general-knowledge escape.

4. 참고자료 listing rules:
   - List ONLY the sources whose excerpt content you actually
     used to ground a specific claim in your answer. Limit to
     1–3 entries.
   - Sources that are merely retrieved but irrelevant to the
     question MUST NOT appear. A retrieved source with no
     contribution to your answer is not a reference.
   - If you used zero External excerpts (e.g., your answer is
     entirely from an Author post or general knowledge), omit
     the 참고자료 block entirely.

   참고자료 (when present):
   - [company - title](url) — 한 줄 설명 (글이 다루는 관점)

5. Universal:
   - Never invent URLs. Only use links that appear in the excerpts.
   - Answer in Korean; keep technical terms in English.
   - If retrieved excerpts do not directly support the answer,
     say plainly "관련 글에서는 직접적인 내용을 찾지 못했습니다.
     일반적으로는 …" and continue with general engineering
     knowledge — do NOT pretend marginally related excerpts
     answer the question.
   - Do not fabricate links between Author posts and External
     source topics. If they discuss unrelated things, treat them
     independently.

Query: {query}

Answer:
