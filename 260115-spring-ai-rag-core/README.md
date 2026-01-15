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

