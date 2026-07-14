# Internal Server AI — 서버·CI가 AI를 호출하는 구성

> 목표: 사람이 없는 workload의 Model API 호출을 identity·비용·품질 기준으로 통제한다.
> 범위 밖: 사람의 좌석형 사용은 [Internal Human AI](./internal-human-ai.md)에서 다룬다.

## 사람 구성과 다른 점

| 항목 | Server AI Runtime |
|---|---|
| 실행 주체 | backend·batch·CI(Workload Principal) |
| 모델 연결 | LiteLLM을 거쳐 Model API 호출 |
| 인증 | service credential·virtual key |
| 비용 | token·request 기반 API Usage |
| 중앙 관측 | end-to-end model·agent trace |

## 서버 페이즈 0 — LiteLLM으로 Model API 통제

```mermaid
---
config:
  theme: base
  darkMode: false
  themeVariables:
    background: "#ffffff"
    primaryColor: "#ffffff"
    primaryTextColor: "#111827"
    primaryBorderColor: "#475569"
    lineColor: "#334155"
---
flowchart LR
  subgraph canvas[" "]
    direction LR
    workload["Backend · Batch · CI\nWorkload Principal"]
    gateway["Model Gateway\nLiteLLM"]
    config["service별 설정\nvirtual key · budget · model"]
    provider["Model Provider API\n종량제"]

    workload -->|"S1 · Model Request"| gateway
    config -.->|"S2 · 정책 로드"| gateway
    gateway -->|"S3 · provider 외부 호출"| provider
    provider -->|"S4 · Model Response"| gateway
    gateway -->|"S5 · Model Response"| workload
  end

  linkStyle 2 stroke:#D13212,stroke-width:2px
  classDef box fill:#ffffff,stroke:#475569,stroke-width:1px,color:#111827
  class workload,gateway,config,provider box
  style canvas fill:#ffffff,stroke:#ffffff,stroke-width:0px,color:#111827
```

- service·environment마다 Workload Principal과 virtual key를 발급한다.
- provider key는 LiteLLM secret에만 저장한다.
- team·service별 budget과 허용 Model ID를 둔다.
- 사용자 seat나 개인 credential을 workload identity로 사용하지 않는다.

**다음 신호:** 서버가 여러 turn의 상태를 관리하거나 사내 tool을 호출해야 한다.

## 서버 페이즈 1 — Agent Host와 Tool 경로 추가

```mermaid
---
config:
  theme: base
  darkMode: false
  themeVariables:
    background: "#ffffff"
    primaryColor: "#ffffff"
    primaryTextColor: "#111827"
    primaryBorderColor: "#475569"
    lineColor: "#334155"
---
flowchart LR
  subgraph canvas[" "]
    direction LR
    client["Backend Client"]
    agent["Agent Host\nstate · model/tool loop"]
    modelGateway["Model Gateway\nLiteLLM"]
    provider["Model Provider API"]
    toolGateway{"Tool Gateway\nWorkload 정책"}
    tools["사내 MCP Servers"]

    client -->|"S1 · Agent Request"| agent
    agent -->|"S2 · Model Request"| modelGateway
    modelGateway -->|"S3 · provider 외부 호출"| provider
    provider -->|"S4 · Tool Proposal"| modelGateway
    modelGateway -->|"S5 · Tool Proposal"| agent
    agent -->|"S6 · Inbound MCP Call"| toolGateway
    toolGateway -->|"S7 · Downstream MCP Call"| tools
    tools -->|"S8 · Tool Result"| toolGateway
    toolGateway -->|"S9 · 필터링 결과"| agent
  end

  linkStyle 2 stroke:#D13212,stroke-width:2px
  classDef box fill:#ffffff,stroke:#475569,stroke-width:1px,color:#111827
  classDef guard fill:#fef3c7,stroke:#d97706,stroke-width:1px,color:#111827
  class client,agent,modelGateway,provider,tools box
  class toolGateway guard
  style canvas fill:#ffffff,stroke:#ffffff,stroke-width:0px,color:#111827
```

Agent Host가 loop를 소유한다. Model Gateway가 Tool Gateway를 호출하지 않고, 두 gateway도 서로 연결되지 않는다. 조직 지식 검색은 필수 구성요소가 아니며 필요할 때 [Knowledge Base](./knowledge-base.md)의 Retrieval API를 tool로 연결한다.

Tool Gateway는 Kotlin + Spring Boot + Spring AI MCP 애플리케이션 하나로 시작한다. 앞에서는 MCP Server, 뒤에서는 MCP Client다. Inbound MCP Call을 그대로 proxy하지 않고 Workload Principal·허용 tool·Effect를 확인한 뒤 새 downstream credential로 실행한다.

**다음 신호:** 민감 READ 또는 WRITE·EGRESS가 필요하다.

## 서버 페이즈 2 — 민감 결과와 WRITE·EGRESS 통제

```mermaid
---
config:
  theme: base
  darkMode: false
  themeVariables:
    background: "#ffffff"
    primaryColor: "#ffffff"
    primaryTextColor: "#111827"
    primaryBorderColor: "#475569"
    lineColor: "#334155"
---
flowchart LR
  subgraph canvas[" "]
    direction LR
    agent["Agent Host"]
    gateway{"Tool Gateway\nWorkload Grant · Effect · Risk"}
    policy["Tool · Content Policy"]
    approval["사람 승인\npayload hash · 만료"]
    tools["민감 READ · WRITE · EGRESS"]
    audit["Security Audit Record"]

    agent -->|"S1 · Inbound tools/call"| gateway
    policy -.->|"S2 · Grant · 마스킹 규칙"| gateway
    gateway -.->|"S3a · 위험하면 승인 요청"| approval
    approval -.->|"S3b · 승인된 고정 payload"| gateway
    gateway -->|"S4 · Downstream tools/call"| tools
    tools -->|"S5 · raw Tool Result"| gateway
    gateway -->|"S6 · masked Result"| agent
    gateway -->|"S7 · 불변 감사 기록"| audit
  end

  classDef box fill:#ffffff,stroke:#475569,stroke-width:1px,color:#111827
  classDef guard fill:#fef3c7,stroke:#d97706,stroke-width:1px,color:#111827
  class agent,policy,approval,tools,audit box
  class gateway guard
  style canvas fill:#ffffff,stroke:#ffffff,stroke-width:0px,color:#111827
```

- JSON schema의 민감 field를 먼저 마스킹하고 자유 텍스트에만 detector를 쓴다.
- WRITE에는 idempotency key를 요구한다.
- `sensitive_data && untrusted_input` 상태의 EGRESS는 자동 실행하지 않는다.
- PostgreSQL polling으로 시작하고 독립 worker가 필요할 때만 Kafka와 DLT를 추가한다.

## 관측

OpenTelemetry를 공통 계측으로 사용한다. Prometheus·Loki·Tempo·Grafana로 운영 지표를 보고, agent trace·evaluation은 Langfuse 또는 LangSmith 하나를 선택한다. LangGraph는 장시간 pause·resume이 필요할 때의 Agent Host runtime이지 모니터링 제품이 아니다.

## 참고 자료

- [LiteLLM — Virtual Keys · Budgets](https://docs.litellm.ai/docs/proxy/virtual_keys)
- [Langfuse Observability](https://langfuse.com/docs/observability/overview)
- [LangSmith Observability](https://docs.langchain.com/langsmith/observability-concepts)
