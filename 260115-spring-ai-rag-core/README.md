# Spring AI RAG Core

Spring AI + pgvector 기반 RAG 파이프라인

## Step 1: 기본 RAG 파이프라인

QuestionAnswerAdvisor를 사용한 자동 RAG 구현

```
질문 → 벡터 검색 → 컨텍스트 조립 → LLM 호출 → 응답
```

```kotlin
// QuestionAnswerAdvisor가 알아서 검색 → 컨텍스트 → LLM 호출
val chatClient = chatClientBuilder
    .defaultAdvisors(QuestionAnswerAdvisor(vectorStore))
    .build()

chatClient.prompt().user("Spring AI가 뭐야?").call()
```

**문제점**
- topK, threshold 제어 불가
- 검색 결과 없어도 LLM 호출됨
- 어떤 문서가 검색됐는지 확인 불가

## Step 2: 수동 RAG - 제어/관찰 가능성

QuestionAnswerAdvisor 제거하고 직접 파이프라인 구현

```kotlin
val chatClient = chatClientBuilder.build()  // Advisor 없이

fun ask(question: String, topK: Int, threshold: Double): AskResult {
    // 1. 검색 (제어 가능)
    val docs = search(question, topK, threshold)

    // 2. 로깅 (관찰 가능)
    log.info("검색 결과: ${docs.size}개, 최고 score: ${docs.maxOf { it.score }}")

    // 3. 조건부 LLM 호출
    if (docs.isEmpty()) return AskResult(llmCalled = false, ...)

    // 4. 컨텍스트 조립 → LLM
    val context = docs.joinToString("\n") { it.content }
    chatClient.prompt()
        .system(SYSTEM_PROMPT)
        .user("컨텍스트:\n$context\n\n질문: $question")
        .call()
}
```

### search vs ask

| | search | ask |
|---|---|---|
| 벡터 검색 | O | O |
| LLM 호출 | X | O |
| 용도 | threshold 튜닝, 디버깅 | 실제 질의응답 |
| 비용 | 낮음 | 높음 |

### 주요 파라미터

| 파라미터 | 설명 | 기본값 |
|----------|------|--------|
| `topK` | 최대 검색 문서 수 | 5 |
| `threshold` | 유사도 임계값 (0.0~1.0) | 0.0 |

## Step 3: 하이브리드 검색 (BM25 + Vector)

벡터 검색(의미 기반)과 BM25(키워드 기반)를 결합하여 검색 품질 개선

### 왜 하이브리드 검색인가?

| 검색 방식 | 장점 | 단점 |
|-----------|------|------|
| **벡터** | 의미적 유사성 | 정확한 키워드 매칭 약함 |
| **BM25** | 키워드 정확 매칭 | 의미적 유사성 없음 |
| **하이브리드** | 둘의 장점 결합 | 복잡도 증가 |

### 아키텍처

```
질문 ──┬──→ VectorStore.similaritySearch() ──→ 벡터 점수
       │
       └──→ PostgreSQL FTS (ts_rank_cd) ──→ BM25 점수
                                              │
                         Min-Max 정규화 ←─────┘
                              │
                    α·vector + (1-α)·bm25 = 하이브리드 점수
```

### 점수 융합

**Min-Max 정규화 + 가중 합산**

```kotlin
// 1. 각 검색 결과를 [0, 1] 범위로 정규화
val normVector = normalizeMinMax(vectorScores)
val normBm25 = normalizeMinMax(bm25Scores)

// 2. alpha 가중치로 결합
val hybridScore = alpha * normVector + (1 - alpha) * normBm25
```

| alpha | 의미 |
|-------|------|
| 0.0 | BM25 only (키워드 검색) |
| 0.5 | 균형 (기본값) |
| 1.0 | Vector only (의미 검색) |

### PostgreSQL Full-Text Search

```sql
-- tsvector 컬럼 + GIN 인덱스
ALTER TABLE vector_store ADD COLUMN content_tsv tsvector;
CREATE INDEX idx_vector_store_tsv ON vector_store USING GIN(content_tsv);

-- 자동 tsvector 생성 트리거
CREATE TRIGGER trg_update_content_tsv
BEFORE INSERT OR UPDATE OF content ON vector_store
FOR EACH ROW EXECUTE FUNCTION update_content_tsv();

-- BM25 유사 랭킹 (ts_rank_cd = Cover Density)
SELECT id, content, ts_rank_cd(content_tsv, plainto_tsquery('simple', ?)) as score
FROM vector_store
WHERE content_tsv @@ plainto_tsquery('simple', ?)
ORDER BY score DESC;
```

### API

```
GET /api/ai/hybrid?query=벡터&alpha=0.5&topK=10
```

**응답**
```json
{
  "query": "벡터",
  "alpha": 0.5,
  "results": [
    {
      "content": "pgvector는 PostgreSQL의 벡터 검색 확장입니다.",
      "hybridScore": 0.85,
      "vectorScore": 0.72,
      "bm25Score": 0.98
    }
  ]
}
```

### 파일 구조

```
src/main/kotlin/dev/hyune/rag/
├── controller/
│   └── RagController.kt      # /hybrid, /clear 엔드포인트
├── service/
│   ├── RagService.kt         # 기존 RAG 서비스
│   ├── Bm25SearchService.kt  # PostgreSQL FTS
│   └── HybridSearchService.kt # 점수 융합
└── dto/
    ├── SearchResult.kt
    ├── AskResult.kt
    └── HybridSearchResult.kt # 하이브리드 검색 결과
```
