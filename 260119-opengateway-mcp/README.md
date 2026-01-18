# OpenGateway MCP Server

OpenGateway API 문서를 검색하는 MCP(Model Context Protocol) 서버입니다.
Claude Desktop, Cursor 등 AI 도구에서 정확한 OpenGateway 연동 코드를 생성할 수 있도록 지원합니다.

## 학습 목표

- MCP(Model Context Protocol) 이해 및 구현
- 마크다운 헤더 기반 문서 청킹
- BM25 키워드 검색 알고리즘

---

## 1. MCP (Model Context Protocol) 이론

### MCP란?

MCP는 **LLM 애플리케이션과 외부 데이터/도구를 연결하는 표준 프로토콜**입니다.

```
┌─────────────────┐         ┌─────────────────┐
│  Claude Desktop │  ←───→  │   MCP Server    │
│  (MCP Host)     │  STDIO  │  (이 프로젝트)   │
└─────────────────┘         └─────────────────┘
```

### MCP 아키텍처

| 컴포넌트 | 설명 | 예시 |
|---------|------|------|
| **Host** | MCP 서버를 호출하는 LLM 애플리케이션 | Claude Desktop, Cursor |
| **Server** | 도구/리소스를 제공하는 서버 | 이 프로젝트 |
| **Tool** | LLM이 호출할 수 있는 함수 | `search_opengateway_docs` |
| **Resource** | 읽기 전용 데이터 소스 | 문서 목록 |

### 전송 방식 비교

| 방식 | 장점 | 단점 | 적합한 환경 |
|------|------|------|------------|
| **STDIO** | 간단, 로컬 실행 | 원격 불가 | 로컬 CLI, Desktop 앱 |
| SSE | 서버→클라 스트리밍 | 양방향 제한 | 웹 대시보드 |
| Streamable-HTTP | 양방향 스트리밍 | 복잡 | 실시간 협업 |

**이 프로젝트**: STDIO 방식 사용 (Claude Desktop 연동)

### Spring AI MCP Server

Spring AI 2.0부터 MCP Server를 쉽게 구현할 수 있는 스타터 제공:

```kotlin
@Service
class OpenGatewayMcpTools {
    @Tool(description = "OpenGateway API 문서를 검색합니다")
    fun searchDocs(query: String): List<SearchResult> {
        // ...
    }
}
```

---

## 2. 문서 청킹 (Chunking) 이론

### 청킹이 필요한 이유

LLM에는 **Context Window** 제한이 있어 전체 문서를 한 번에 전달할 수 없습니다.
문서를 의미 있는 단위로 분할(청킹)하여 관련 부분만 전달해야 합니다.

### 청킹 전략 비교

| 전략 | 장점 | 단점 | 적합한 문서 |
|------|------|------|------------|
| **헤더 기반** | 의미 단위 보존 | 헤더 없으면 불가 | API 문서, 기술 문서 ✅ |
| 토큰/고정 길이 | 구현 간단 | 의미 단위 깨짐 | 소설, 일반 텍스트 |
| 시맨틱 | 자동 감지 | 한국어 불안정 | 연구용 |

**이 프로젝트**: 마크다운 헤더(`##`, `###`) 기반 청킹

### 헤더 기반 청킹 예시

```markdown
# OpenGateway API
## 인증           ← 청크 1
헤더에 토큰을 포함...

## 요청 형식      ← 청크 2
POST /v1/chat...

### 파라미터      ← 청크 3 (2의 하위)
- model: 모델명
```

---

## 3. BM25 검색 이론

### RDB 개발자를 위한 비유

검색 엔진의 개념을 RDB 용어로 이해하면 쉽습니다:

| 검색 엔진 | RDB | 설명 |
|----------|-----|------|
| **IDF (희귀도)** | **카디널리티** | 고유한 값이 많을수록 구별에 유용 |
| **낮은 IDF** | **낮은 선택도** | `gender = 'M'` → 50% 필터링 (별로) |
| **높은 IDF** | **높은 선택도** | `user_id = 123` → 정확히 1건 (좋음) |
| **역인덱스** | **B-Tree 인덱스** | 값 → 위치 매핑 |
| **불용어 제거** | **인덱스 제외 컬럼** | 의미 없는 데이터 제외 |

**핵심 통찰**: 둘 다 **"희귀할수록 구별하는 데 가치있다"**

```
RDB:  WHERE status = 'active'     -- 90%가 active면 인덱스 효율 낮음
검색: "API"로 검색                 -- 거의 모든 문서 매칭 → 구별 안 됨

RDB:  WHERE user_id = 12345       -- 정확히 1건 (선택도 높음)
검색: "x-opengateway-user-id"     -- 1~2개 문서 (IDF 높음)
```

---

### TF-IDF 이해하기

**TF (Term Frequency)**: 문서 내 단어 출현 빈도
```
"인증 방법을 설명합니다. 인증 헤더에..."
→ "인증" TF = 2
```

**IDF (Inverse Document Frequency)**: 전체 문서에서의 희귀도
```
전체 100개 문서 중:
- "API" → 95개 문서에 등장 → IDF 낮음 (선택도 낮음)
- "Bearer" → 2개 문서에 등장 → IDF 높음 (선택도 높음)
```

**TF × IDF = 최종 점수**
- 해당 문서에 많이 나오면서 (TF ↑)
- 다른 문서엔 잘 안 나오는 (IDF ↑)
- → 검색에 중요한 단어

---

### BM25: TF-IDF의 개선

TF-IDF 문제점:
```
TF = 10  → 점수 10
TF = 100 → 점수 100  ← 정말 10배 더 관련있나? 아님
```

BM25 해결책 (포화 곡선):
```
TF = 1   → 점수 ~1.0
TF = 10  → 점수 ~2.0
TF = 100 → 점수 ~2.1  ← 포화됨 (수확 체감)
```

### BM25 공식

```
score(D, Q) = Σ IDF(qi) × (TF × (k1 + 1)) / (TF + k1 × (1 - b + b × |D|/avgdl))
```

| 파라미터 | 의미 | 일반적 값 |
|---------|------|----------|
| k1 | TF 포화 속도 | 1.2 ~ 2.0 |
| b | 문서 길이 보정 | 0.75 |
| avgdl | 평균 문서 길이 | 계산값 |

---

### 역인덱스 (Inverted Index)

RDB의 B-Tree 인덱스가 `컬럼값 → Row ID`를 매핑하듯,
역인덱스는 `단어 → 문서 ID 목록`을 매핑합니다.

```
역인덱스:
"인증"   → [doc1, doc3]
"헤더"   → [doc1, doc2, doc3]
"Bearer" → [doc1]

검색: "인증 헤더"
1. "인증" 조회 → [doc1, doc3]
2. "헤더" 조회 → [doc1, doc2, doc3]
3. 점수 계산 → doc1이 둘 다 매칭 → 최고 점수
```

---

### 구현 선택: 인메모리 vs 인메모리 DB

| 옵션 | 장점 | 단점 |
|-----|------|------|
| **직접 구현 (선택)** | 알고리즘 이해, 의존성 없음 | 직접 유지보수 |
| SQLite FTS5 | 검증됨, 가벼움 | 추가 의존성 |
| Lucene 인메모리 | 최고 성능 | 무거움 |

**이 프로젝트**: 학습 목적 + 문서 수백 개 수준이라 직접 구현

---

## 실행 방법

### 1. 빌드

```bash
cd 260119-opengateway-mcp
./gradlew bootJar
```

### 2. Claude Desktop 설정

`~/Library/Application Support/Claude/claude_desktop_config.json`:

```json
{
  "mcpServers": {
    "opengateway-docs": {
      "command": "java",
      "args": ["-jar", "/path/to/opengateway-mcp.jar"]
    }
  }
}
```

### 3. 사용

Claude에서 다음과 같이 질문:
- "OpenGateway 인증 방법 알려줘"
- "OpenGateway 연동 코드 만들어줘"

---

## 프로젝트 구조

```
260119-opengateway-mcp/
├── src/main/kotlin/dev/hyune/mcp/
│   ├── McpApplication.kt          # 메인 애플리케이션
│   ├── tool/                      # MCP Tool 정의
│   │   └── OpenGatewayMcpTools.kt
│   ├── document/                  # 문서 청킹/파싱
│   │   ├── MarkdownChunker.kt
│   │   └── DocumentStore.kt
│   └── search/                    # BM25 검색
│       └── Bm25SearchService.kt
└── src/main/resources/
    ├── application.yml
    └── docs/                      # OpenGateway API 문서
        └── opengateway-api.md
```

---

## 참고 자료

- [MCP 공식 문서](https://modelcontextprotocol.io/)
- [Spring AI MCP](https://docs.spring.io/spring-ai/reference/api/mcp.html)
- [토스페이먼츠 MCP 사례](https://toss.tech/article/tosspayments-mcp)
