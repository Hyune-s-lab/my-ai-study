# Spring AI RAG Core

Spring AI + pgvector 기반 RAG 파이프라인

## 학습 목표

1. RAG 기본 파이프라인 이해
2. 벡터 검색 파라미터 튜닝 (topK, threshold)
3. 수동 RAG vs 자동 RAG (QuestionAnswerAdvisor)
4. 관찰 가능성 (로깅, 검색 결과 노출)

## 핵심 개념

### RAG 파이프라인

```
질문 → 벡터 검색 → 컨텍스트 조립 → LLM 호출 → 응답
```

### 자동 RAG vs 수동 RAG

```kotlin
// 자동 RAG (QuestionAnswerAdvisor)
// 장점: 간편함
// 단점: 제어 불가, 검색 결과 없어도 LLM 호출
val chatClient = chatClientBuilder
    .defaultAdvisors(QuestionAnswerAdvisor(vectorStore))
    .build()

// 수동 RAG (이 프로젝트)
// 장점: topK/threshold 제어, 검색 결과 없으면 LLM 생략, 로깅
// 단점: 직접 구현 필요
val docs = search(question, topK, threshold)
if (docs.isEmpty()) return "검색 결과 없음"
val context = docs.joinToString("\n") { it.content }
chatClient.prompt().system(SYSTEM_PROMPT).user("컨텍스트:\n$context\n\n질문: $question")
```

### search vs ask

| | search | ask |
|---|---|---|
| 벡터 검색 | O | O |
| LLM 호출 | X | O |
| 용도 | 검색 결과 확인, threshold 튜닝 | 실제 질의응답 |
| 비용 | 낮음 | 높음 |

## 실행

```bash
# 1. pgvector 실행
docker compose up -d

# 2. 환경변수 설정 (IntelliJ EnvFile 또는 export)
export OPENAI_API_KEY=sk-xxx

# 3. 앱 실행
./gradlew :260115-spring-ai-rag-core:bootRun
```

## API

### 문서 인덱싱

```http
POST /api/ai/index
Content-Type: application/json

{
  "documents": [
    "Spring AI는 AI 애플리케이션을 쉽게 만들 수 있게 해줍니다.",
    "pgvector는 PostgreSQL의 벡터 검색 확장입니다."
  ]
}
```

### RAG 질의응답

```http
POST /api/ai/ask
Content-Type: application/json

{
  "question": "Spring AI가 뭐야?",
  "topK": 5,
  "threshold": 0.5
}
```

응답:
```json
{
  "question": "Spring AI가 뭐야?",
  "answer": "Spring AI는 AI 애플리케이션을 쉽게 만들 수 있게 해주는 프레임워크입니다.",
  "searchResults": [
    { "content": "Spring AI는...", "score": 0.82, "metadata": {} }
  ],
  "llmCalled": true
}
```

### 벡터 검색 (LLM 없이)

```http
GET /api/ai/search?query=벡터&topK=3&threshold=0.7
```

## 주요 파라미터

| 파라미터 | 설명 | 기본값 |
|----------|------|--------|
| `topK` | 최대 검색 문서 수 | 5 |
| `threshold` | 유사도 임계값 (0.0~1.0) | 0.0 |

- `threshold=0.0`: 모든 문서 반환
- `threshold=0.7`: 유사도 70% 이상만 반환
- `threshold=0.95`: 매우 유사한 문서만 (결과 없을 수 있음)

## 설정

### pgvector

```yaml
spring:
  ai:
    vectorstore:
      pgvector:
        index-type: HNSW          # 검색 알고리즘 (HNSW 권장)
        distance-type: COSINE_DISTANCE  # 유사도 계산 (텍스트는 COSINE)
        dimensions: 1536          # 임베딩 차원 (모델에 따라 다름)
```

### OpenAI

```yaml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      embedding:
        options:
          model: text-embedding-3-small
      chat:
        options:
          model: gpt-4o
          temperature: 0.7
```

## 다음 학습

- [ ] 하이브리드 검색 (BM25 + 벡터)
- [ ] 리랭커 (Reranker)
- [ ] 청킹 전략
- [ ] RAG 평가 메트릭
