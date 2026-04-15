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
├── core/
│   ├── api/
│   │   ├── config/          # AppConfig, CacheConfig, ChatClientConfig, WebConfig
│   │   └── controller/
│   │       ├── ApiControllerAdvice.kt
│   │       └── v1/
│   │           ├── AdminController, ChatController, SimilarController, TrendingController
│   │           ├── request/   # ChatRequest, SimilarRequest
│   │           └── response/  # ArticleAdminResponse, SimilarResponse, TrendingResponse, ...
│   ├── domain/
│   │   ├── article/         # ArticleEmbedService, ArticleChunkService
│   │   ├── blog/            # BlogCacheService
│   │   ├── chat/            # ChatService
│   │   ├── crawl/           # CrawlService, ArticleSaveService, RssFeedParser, ContentCleaner, SlackNotifier
│   │   ├── similar/         # SimilarService
│   │   └── trending/        # HnTrendingService, HnTrendingScheduler
│   └── support/
│       ├── error/           # CoreException, ErrorType, ErrorMessage
│       ├── properties/      # AdminProperties, SimilarProperties, SlackProperties
│       └── response/        # ApiResponse, ResultType, PageResponse
├── scheduler/               # CrawlScheduler, EmbeddingRetryScheduler
└── storage/
    ├── article/             # ArticleEntity, ArticleChunkEntity, Repositories
    ├── blog/                # BlogEntity, BlogRepository
    ├── chat/                # ChatSessionEntity, ChatSessionRepository
    └── trending/            # HnTrendingEntity, HnTrendingRepository
```

## 핵심 흐름

1. **크롤링**: 매주 월 03:00, 블로그별 독립 실패 허용, url_hash 중복 체크
2. **임베딩**: 매시간, embedding IS NULL 최대 50건, title 2회 반복 + content 800자
3. **추천**: blog-api에서 벡터 수신 → 코사인 유사도 Top5, Caffeine 캐시
4. **챗봇**: Spring AI ChatClient + RetrievalAugmentationAdvisor + SSE 스트리밍
5. **트렌딩**: 매일 09:00, HN frontpage points>=100

## 코딩 컨벤션

### 패키지 & 파일 위치

- **core/api**: config, controller (v1/ 하위에 버전별 배치), request/response DTO
- **core/domain**: 도메인별 서비스 로직 (`domain/{도메인}/` 아래 배치)
- **core/support**: error, properties, response 등 공통 지원 모듈
- **scheduler/**: 스케줄러 (CrawlScheduler, EmbeddingRetryScheduler)
- **storage/**: entity, repository (`storage/{도메인}/` 아래 배치)

### 로깅

```kotlin
// kotlin-logging 사용 (SLF4J 직접 사용 X)
private val logger = KotlinLogging.logger {}

logger.info { "메시지: $variable" }
logger.warn { "[ERROR_TYPE] message" }
logger.error(e) { "에러 메시지" }
```

### 예외 처리

- `ErrorType` enum에 status + message + logLevel 정의
- `CoreException(ErrorType)` throw — service 레이어에서만
- `ApiControllerAdvice`에서 `ApiResponse.error()` 반환
- controller에서 예외 catch 하지 않음

### 응답 래퍼

```kotlin
// 모든 API 응답은 ApiResponse<T>로 래핑
ApiResponse.ok(data)        // 성공
ApiResponse.ok()            // 성공 (데이터 없음)
ApiResponse.error(ErrorType.NOT_FOUND)  // 실패
```

### DTO 규칙

- **Request DTO**: `core/api/controller/v1/request/` 아래 별도 파일
- **Response DTO**: `core/api/controller/v1/response/` 아래 별도 파일
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
