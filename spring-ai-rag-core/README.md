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

## 검색 기술 비교

### 전통 RDB vs Lexical vs Semantic

| | 전통 RDB | Lexical (BM25) | Semantic (Vector) |
|---|---|---|---|
| 방식 | 정확히 일치 | 단어 빈도 + 점수 | 의미 유사도 |
| 결과 | Yes/No | 순위 | 순위 |
| 예시 | `WHERE name = 'dan'` | Elasticsearch, FTS | pgvector |
| 속도 | ⚡⚡⚡ | ⚡⚡ | ⚡ (임베딩 병목) |
| 정확도 | 낮음 | 중간 | 높음 |

```
전통 RDB     →  "이 값이 있나요?" (일치 여부)
Lexical      →  "이 단어가 얼마나 나오나요?" (빈도 기반 순위)
Semantic     →  "이 의미와 얼마나 비슷한가요?" (임베딩 기반 순위)
Hybrid       →  Lexical + Semantic (정확도 ↑, 속도 ↓)
```

### BM25란?

**Best Match 25** - 1994년 연구의 25번째 버전이 표준이 됨

```
BM25 ≈ TF × IDF × 문서길이 보정

TF  = 단어 빈도 (많이 나올수록 ↑)
IDF = 희귀도 (드문 단어일수록 ↑)
```

Elasticsearch, PostgreSQL FTS, Lucene 모두 BM25 기반

### 전통 RDB vs BM25

검색어 형식이 **100% 고정**이면 전통 RDB, **변형 가능**하면 BM25

```
"민법 제750조" 검색 시:

전통 RDB (LIKE '%민법 제750조%')
  → "민법 750조" ❌ 못 찾음
  → "민법제750조" ❌ 못 찾음

BM25 (토큰: "민법", "제750조")
  → "민법 750조" ✅ 찾음
  → "민법제750조" ✅ 찾음
```

## 도메인별 검색 전략

| 도메인 | 추천 | 이유 |
|--------|------|------|
| **주문/결제 조회** | 전통 RDB | 주문번호, 거래ID 정확히 일치 |
| **회원 조회** | 전통 RDB | 이메일, 아이디 정확히 일치 |
| **재고/상품코드** | 전통 RDB | SKU, 바코드 정확히 일치 |
| **법률/의료 문서** | BM25 | 입력 변형 대응 ("민법 750조", "제750조") |
| **코드 검색** | BM25 | 함수명, 변수명 정확 매칭 |
| **로그 검색** | BM25 | 에러코드, IP 등 정확한 값 |
| **고객 Q&A** | Vector | 같은 질문 다르게 표현 ("환불" = "돈 돌려줘") |
| **이커머스 검색** | Hybrid | "나이키 운동화" (브랜드=키워드, 운동화=의미) |
| **RAG 챗봇** | Hybrid | 질문 의도 + 정확한 용어 둘 다 |
