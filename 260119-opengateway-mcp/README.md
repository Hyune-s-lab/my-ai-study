# OpenGateway MCP Server

OpenGateway API 문서를 검색하는 MCP(Model Context Protocol) 서버입니다.  
Claude Desktop, Cursor 등 AI 도구에서 정확한 OpenGateway 연동 코드를 생성할 수 있도록 지원합니다.

## 학습 목표

- MCP(Model Context Protocol) 이해 및 구현
- 문서 청킹 이론 (Spring AI MarkdownDocumentReader 사용)
- BM25 키워드 검색 알고리즘 직접 구현

---

## 1. MCP (Model Context Protocol) 이론

### MCP란?

MCP는 **LLM 애플리케이션과 외부 데이터/도구를 연결하는 표준 프로토콜**입니다.

```
┌─────────────────┐         ┌─────────────────┐
│  MCP Clients    │         │   MCP Server    │
│  - Claude       │  ←───→  │  (Smithery 등)  │
│  - Cursor       │   SSE   │                 │
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

**이 프로젝트**: SSE 방식 사용 (HTTP 기반, 원격 연동 가능)

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

**이 프로젝트**: Spring AI `MarkdownDocumentReader` 사용

### 헤더 기반 청킹 예시

```markdown
# OpenGateway API
## 인증           ← 청크 1
헤더에 토큰을 포함...

## 요청 형식      ← 청크 2
POST /v1/chat...

### 파라미터      ← 청크 3
- model: 모델명
```

Spring AI가 헤더 레벨(level)을 메타데이터로 제공하므로, 필요시 계층 구조로 활용 가능.

---

## 3. BM25 검색 이론

### TF-IDF 이해하기

**TF (Term Frequency)**: 문서 내 단어 출현 빈도
```
"인증 방법을 설명합니다. 인증 헤더에..."
→ "인증" TF = 2
```

**IDF (Inverse Document Frequency)**: 전체 문서에서의 희귀도

| IDF | RDB 비유 | 예시 |
|-----|----------|------|
| **낮은 IDF** | 낮은 선택도 | `gender = 'M'` → 50% 필터링 (별로) |
| **높은 IDF** | 높은 선택도 | `user_id = 123` → 정확히 1건 (좋음) |

**핵심**: 희귀할수록 구별하는 데 가치있다

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

| 옵션 | 장점 | 단점 | 적합한 케이스 |
|-----|------|------|--------------|
| **직접 구현 (선택)** | 알고리즘 이해, 의존성 없음 | 직접 유지보수 | 학습, 소규모 |
| SQLite FTS5 | 검증됨, 텍스트 검색 최적화 | 추가 의존성 | 문서 검색 |
| DuckDB | 분석 쿼리에 강함 | FTS 미성숙 | 로그 분석, 통계 |
| Lucene 인메모리 | 최고 성능 | 무거움 | 대규모 검색 |

**SQLite FTS5 vs DuckDB**:
- SQLite: OLTP(트랜잭션) 설계, FTS5 내장으로 텍스트 검색에 최적
- DuckDB: OLAP(분석) 설계, 집계/스캔에 강하지만 FTS는 확장 기능

**이 프로젝트**: 학습 목적 + 문서 수백 개 수준이라 직접 구현

---

## 실행 방법

### Docker 실행

```bash
# 프로젝트 루트에서
docker build -f 260119-opengateway-mcp/Dockerfile -t opengateway-mcp .
docker run -p 8080:8080 opengateway-mcp
```

서버가 http://localhost:8080 에서 시작됩니다.

### MCP 클라이언트 설정

`~/Library/Application Support/Claude/claude_desktop_config.json`:

```json
{
  "mcpServers": {
    "opengateway-docs": {
      "url": "http://localhost:8080/sse"
    }
  }
}
```

### 사용

Claude에서 다음과 같이 질문:
- "OpenGateway 인증 방법 알려줘"
- "OpenGateway 연동 코드 만들어줘"

---

## 참고 자료

- [MCP 공식 문서](https://modelcontextprotocol.io/)
- [Spring AI MCP](https://docs.spring.io/spring-ai/reference/api/mcp.html)
- [토스페이먼츠 MCP 사례](https://toss.tech/article/tosspayments-mcp)
