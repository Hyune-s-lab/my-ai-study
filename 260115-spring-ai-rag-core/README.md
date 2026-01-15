# Spring AI RAG Core

Spring AI와 PostgreSQL + pgvector 기반의 RAG(Retrieval Augmented Generation) 파이프라인 구현

## 핵심 개념

### RAG란?
검색 증강 생성(Retrieval Augmented Generation)은 LLM이 응답을 생성하기 전에 관련 문서를 검색하여 컨텍스트로 제공하는 기법이다.

```
[질문] → [벡터 검색] → [관련 문서 추출] → [LLM + 컨텍스트] → [응답]
```

### 아키텍처

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│   Client    │────▶│  Spring AI  │────▶│   OpenAI    │
└─────────────┘     └──────┬──────┘     └─────────────┘
                          │
                   ┌──────▼──────┐
                   │   pgvector  │
                   │ (Vector DB) │
                   └─────────────┘
```

## API 엔드포인트

| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | `/api/ai/index` | 문서 임베딩 저장 |
| POST | `/api/ai/ask` | RAG 기반 질의응답 |
| GET | `/api/ai/search` | 유사도 검색 |

## 설정 옵션

### pgvector 설정

```yaml
spring:
  ai:
    vectorstore:
      pgvector:
        initialize-schema: true
        index-type: HNSW
        distance-type: COSINE_DISTANCE
        dimensions: 1536
```

#### index-type (인덱스 알고리즘)

| 값 | 설명 | 특징 |
|----|------|------|
| `HNSW` | Hierarchical Navigable Small World | 가장 빠름, 메모리 사용 높음, **권장** |
| `IVFFlat` | Inverted File with Flat Compression | 빌드 빠름, 검색 느림 |
| `NONE` | 인덱스 없음 | 정확도 100%, 대용량에서 느림 |

#### distance-type (거리/유사도 계산)

| 값 | 설명 | 사용 케이스 |
|----|------|-------------|
| `COSINE_DISTANCE` | 코사인 거리 | 텍스트 임베딩에 **권장** |
| `EUCLIDEAN_DISTANCE` | 유클리드 거리 | 이미지, 수치 데이터 |
| `NEGATIVE_INNER_PRODUCT` | 내적의 음수 | 정규화된 벡터 |

#### dimensions (임베딩 차원)

| 모델 | 차원 |
|------|------|
| OpenAI text-embedding-3-small | 1536 (기본) |
| OpenAI text-embedding-3-large | 3072 |
| OpenAI text-embedding-ada-002 | 1536 |

### OpenAI 설정

```yaml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      chat:
        options:
          model: gpt-4o              # 사용할 모델
          temperature: 0.7           # 창의성 (0.0 ~ 2.0)
          max-tokens: 1000           # 최대 토큰 수
      embedding:
        options:
          model: text-embedding-3-small
```

## 실행 방법

```bash
# 1. Docker 실행 (pgvector)
cd docker && docker compose up -d

# 2. 환경변수 설정
# .env.local 파일에 OPENAI_API_KEY 설정

# 3. 애플리케이션 실행
./gradlew :260115-spring-ai-rag-core:bootRun
```

## 참고 자료

- [Spring AI RAG Documentation](https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html)
- [Spring AI pgvector Documentation](https://docs.spring.io/spring-ai/reference/api/vectordbs/pgvector.html)
- [Spring AI OpenAI Documentation](https://docs.spring.io/spring-ai/reference/api/chat/openai-chat.html)
- [pgvector GitHub](https://github.com/pgvector/pgvector)
- [HNSW Algorithm Paper](https://arxiv.org/abs/1603.09320)
