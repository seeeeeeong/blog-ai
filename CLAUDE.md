# blog-ai

기업 기술블로그 크롤링 + 임베딩 + 추천 + RAG 챗봇 서버

## 기술 스택

- Kotlin + Spring Boot 3.5.x
- Spring AI 1.0.0 (OpenAI text-embedding-3-small, GPT)
- Spring Data JPA + Flyway
- PostgreSQL 16 + pgvector
- Spring Cache (Caffeine)
- ROME (RSS 파싱), Jsoup (HTML 파싱)
- springdoc-openapi (Swagger UI)

## 아키텍처

```
[CrawlScheduler] → RSS → articles(DB) + search_vector
[EmbeddingRetryScheduler] → OpenAI → articles.embedding + article_chunks.embedding
[blog-api] → POST /api/v1/similar → 코사인 유사도 Top5 반환
[사용자] → POST /api/v1/chat → Spring AI RAG + SSE 스트리밍
[HnTrendingScheduler] → hnrss.org → hn_trending(DB)
```

## 패키지 구조

```
com.blog.ai
├── config/              # Properties, Cache, CORS, ChatClient, JpaConfig
├── common/
│   ├── dto/             # ApiResponse<T>
│   ├── entity/          # BaseTimeEntity
│   ├── exception/       # BusinessException, ErrorType, GlobalExceptionHandler
│   └── StopWords.kt
└── domain/
    ├── blog/
    │   ├── entity/      # BlogEntity
    │   ├── repository/  # BlogRepository
    │   └── service/     # BlogCacheService
    ├── article/
    │   ├── controller/  # AdminController
    │   ├── entity/      # ArticleEntity
    │   ├── repository/  # ArticleRepository, ArticleChunkRepository
    │   └── service/     # ArticleEmbedService, ArticleChunkService
    ├── crawl/
    │   ├── dto/         # CrawlResult
    │   ├── scheduler/   # CrawlScheduler, EmbeddingRetryScheduler
    │   └── service/     # CrawlService, RssFeedParser, ContentCleaner
    ├── embedding/
    │   └── client/      # EmbeddingClient
    ├── similar/
    │   ├── controller/  # SimilarController
    │   ├── dto/         # SimilarResponse
    │   └── service/     # SimilarService
    ├── chat/
    │   ├── controller/  # ChatController
    │   └── service/     # ChatService
    └── trending/
        ├── controller/  # TrendingController
        ├── dto/         # TrendingResponse
        ├── entity/      # HnTrendingEntity
        ├── repository/  # HnTrendingRepository
        └── service/     # HnTrendingService, HnTrendingScheduler
```

## 핵심 흐름

1. **크롤링**: 매주 월 03:00, 블로그별 독립 실패 허용, url_hash 중복 체크
2. **임베딩**: 매시간, embedding IS NULL 최대 50건, title 2회 반복 + content 800자
3. **추천**: blog-api에서 벡터 수신 → 코사인 유사도 Top5, Caffeine 캐시
4. **챗봇**: Spring AI ChatClient + RetrievalAugmentationAdvisor + SSE 스트리밍
5. **트렌딩**: 매일 09:00, HN frontpage points>=100

## 코딩 컨벤션

### 패키지 & 파일 위치

- **domain 중심 구조**: controller, dto, entity, repository, service 모두 `domain/{도메인}/` 아래 배치
- web/ 레이어 분리 **하지 않음** — controller도 domain 안에 위치
- config/는 최상위, common/에 공통 유틸리티

### 로깅

```kotlin
// kotlin-logging 사용 (SLF4J 직접 사용 X)
private val logger = KotlinLogging.logger {}

logger.info { "메시지: $variable" }
logger.warn { "[ERROR_TYPE] message" }
logger.error(e) { "에러 메시지" }
```

### 예외 처리

- `ErrorType` enum에 status + message 정의
- `BusinessException(ErrorType)` throw — service 레이어에서만
- `GlobalExceptionHandler`에서 `ApiResponse.error()` 반환
- controller에서 예외 catch 하지 않음

### 응답 래퍼

```kotlin
// 모든 API 응답은 ApiResponse<T>로 래핑
ApiResponse.ok(data)        // 성공
ApiResponse.ok()            // 성공 (데이터 없음)
ApiResponse.error(ErrorType.NOT_FOUND)  // 실패
```

### DTO 규칙

- **Request DTO**: Controller 내부 nested `data class`로 선언
- **Response DTO**: `domain/{도메인}/dto/` 아래 별도 파일
- Response에 nested data class 허용 (e.g., `SimilarResponse.SimilarArticle`)

### Entity 규칙

- `@Id` 필드는 마지막에 위치, `val id: Long = 0L`
- `@Column(nullable = false)` 명시
- `@Enumerated(EnumType.STRING)` 사용
- `@ManyToOne(fetch = FetchType.LAZY)` 기본
- 타임스탬프: `OffsetDateTime` 사용 (LocalDateTime X)

### Trailing Comma

- 모든 파라미터 목록, 프로퍼티 목록, 어노테이션 인자에 trailing comma 사용

```kotlin
data class Example(
    val name: String,
    val value: Int,  // <- trailing comma
)
```

### 서비스 규칙

- 생성자 주입 (primary constructor)
- `@Transactional` — 변경 메서드
- `@Transactional(readOnly = true)` — 조회 메서드
- 비즈니스 예외는 `BusinessException` throw

### 컨트롤러 규칙

- `@Tag` + `@Operation` Swagger 어노테이션 필수
- `@Valid @RequestBody` 검증
- validation message는 한국어
- 응답은 `ApiResponse<T>`로 래핑

### 네이밍

- 클래스: PascalCase (`ArticleEmbedService`)
- 메서드: camelCase, 동사 시작 (`findSimilar`, `crawlAll`, `embedPending`)
- Enum 값: UPPER_SNAKE_CASE (`ARTICLE_NOT_FOUND`)

## 빌드 & 실행

```bash
# PostgreSQL + pgvector 필요
docker-compose up -d

# 실행
./gradlew bootRun

# 환경변수
OPENAI_API_KEY=...
ADMIN_API_KEY=...
SLACK_WEBHOOK_URL=...  # optional
```

## 포트

- blog-ai: 8081
- blog-api: 8080 (별도 프로젝트)

## API 엔드포인트

| Method | Path | 설명 |
|--------|------|------|
| POST | `/api/v1/similar` | 유사 아티클 검색 |
| POST | `/api/v1/chat` | RAG 챗봇 (SSE) |
| GET | `/api/v1/trending` | HN 트렌딩 조회 |
| POST | `/admin/crawl` | 수동 크롤링 (X-Admin-Key) |
| POST | `/admin/embed/retry` | 임베딩 재시도 |
| GET | `/admin/articles` | 아티클 목록 조회 |
