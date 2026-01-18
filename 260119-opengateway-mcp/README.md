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

### TF-IDF 복습

- **TF (Term Frequency)**: 문서 내 단어 출현 빈도
- **IDF (Inverse Document Frequency)**: 전체 문서에서의 희귀도

### BM25 공식

```
score(D, Q) = Σ IDF(qi) × (TF(qi, D) × (k1 + 1)) / (TF(qi, D) + k1 × (1 - b + b × |D|/avgdl))
```

| 파라미터 | 의미 | 일반적 값 |
|---------|------|----------|
| k1 | TF 포화 속도 | 1.2 ~ 2.0 |
| b | 문서 길이 보정 | 0.75 |
| avgdl | 평균 문서 길이 | 계산값 |

### 역인덱스 (Inverted Index)

```
"인증" → [doc1, doc3]
"헤더" → [doc1, doc2, doc3]
"토큰" → [doc1]
```

검색 시 쿼리 토큰들의 posting list를 조회하여 점수 계산

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
