# Enterprise AI Platform 유비쿼터스 언어

> `최종 용어`를 문서와 코드에서 우선 사용한다. `대안·피할 표현`은 같은 말로 오해하기 쉬운 표현이다. `OG 공개 언어`는 OpenGateway 문서를 비교할 때만 사용한다.

## 내부 AI 활용

| 최종 용어 | 정의 | 대안·피할 표현 | OG 공개 언어 |
|---|---|---|---|
| **Internal AI Platform** | 사람과 workload의 AI 사용을 사내 지식·도구·정책에 연결하는 내부 경계 | Internal Gen AI Gateway, AX 전체 | — |
| **Internal Human AI** | 사람이 named seat로 Coding Agent를 직접 사용하는 내부 AI 구성 | Human AI Workspace, human gateway | — |
| **Internal Server AI** | workload가 Model API와 tool을 호출하는 내부 AI 구성 | Server AI Runtime, backend AI | — |
| **Coding Agent** | IDE·CLI에서 model·file·shell·tool loop를 실행하는 Codex·Claude Code류 제품 | Model Gateway, MCP Server | — |
| **Agent Host** | 서버에서 model/tool loop와 상태를 소유하는 실행 주체 | gateway, agent 전체 | — |
| **Model Gateway** | 종량제 Model API의 credential·route·budget·usage를 통제하는 서버 경계 | Tool Gateway, Coding Agent proxy | Gateway |
| **Named Seat** | 특정 사람에게 배정된 provider workspace 사용 권리 | 공유 계정, concurrent license | Team member |
| **Plan Usage** | 좌석형 플랜에 포함되거나 credit로 차감되는 사용량 | API Usage | — |
| **API Usage** | Model API에서 token·request 단위로 측정되는 종량제 사용량 | Plan Usage | usage, tokens |
| **Subscription Pooling** | 사용자 플랜 credential을 공용 서버 capacity로 회전시키는 금지 방식 | credential pool, seat 공유 | — |

## Tool과 Knowledge

| 최종 용어 | 정의 | 대안·피할 표현 | OG 공개 언어 |
|---|---|---|---|
| **Tool Gateway** | 앞에서는 MCP Server, 뒤에서는 MCP Client로 동작하며 실행 정책을 집행하는 경계 | Act Gateway, Model Gateway, 단순 MCP proxy | — |
| **Knowledge Base** | 조직 지식을 수집·정규화하고 출처·소유권·최신성·권한과 함께 관리하는 전체 시스템 | Context Provider, vector DB, Wiki 하나 | — |
| **Knowledge Source** | 원본 지식이 생성·수정되는 Git·Notion·DB·운영 시스템 | Knowledge Base 복제본 | — |
| **Knowledge Record** | source·owner·freshness·access scope를 가진 정규화된 지식 단위 | chunk만, embedding만 | — |
| **Retrieval API** | Knowledge Base의 search·get을 사람·서비스·AI adapter에 제공하는 interface | Knowledge Base 전체, Tool Gateway | — |
| **Context Provider** | AI에 컨텍스트를 제공하는 Retrieval API 또는 adapter를 가리키던 제한적 표현 | Knowledge Base 전체 이름 | — |
| **Tool Exposure Set** | 현재 principal에게 노출 가능한 tool schema 집합 | 전체 catalog, 실행 권한 | — |
| **Tool Proposal** | 모델이 선택한 tool과 arguments이며 아직 실행 권한이 없는 제안 | Tool 실행, 승인 | — |
| **Execution Decision** | Tool Gateway의 실행·차단·승인 대기 판정 | 모델 선택 | — |
| **Inbound MCP Call** | Coding Agent·Agent Host가 Tool Gateway에 보내는 호출 | downstream 호출 | — |
| **Downstream MCP Call** | Tool Gateway가 허용 후 실제 MCP Server에 새로 보내는 호출 | inbound의 단순 전달 | — |
| **Tool Effect** | 내부 읽기·외부 읽기·쓰기·외부 송신의 영향 분류 | role, scope | — |
| **Risk Label** | content가 민감 데이터와 비신뢰 입력을 거쳤는지 나타내는 상태 | safe boolean, PII 여부 | — |
| **Approval Request** | tool·arguments hash·principal·만료에 묶인 1회 승인 대상 | 항상 허용, Slack 버튼 | — |

## 외부 tenant용 Model API

| 최종 용어 | 정의 | 대안·피할 표현 | OG 공개 언어 |
|---|---|---|---|
| **Public Model API Gateway** | tenant의 모델 요청을 인증·라우팅·계측·과금하는 상용 제품 경계 | public AI platform, model mapper | Gateway, OpenAI-compatible gateway |
| **Tenant** | 데이터·정책·비용이 격리되는 고객 조직 | team, account, customer 혼용 | Team, team account |
| **API Key** | tenant workload가 Data Plane을 호출하는 credential | account, model key | API key |
| **Model ID** | client에 노출하는 불변 `owner/versioned_model_name` | latest alias, deployment | model ID, `owner/model` |
| **Venue** | region·account·endpoint·credential로 정해지는 실제 호출 지점 | provider, model | Provider integration |
| **Model Request** | client가 제출한 하나의 논리적 요청 | attempt | API request |
| **Provider Attempt** | Model Request를 한 Venue에 실제 호출한 한 번의 시도 | request | routing attempt |
| **Retry** | 같은 Venue 재호출 | failover, fallback | retry |
| **Failover** | 같은 Model ID·API Contract를 다른 Venue에서 호출 | model fallback | failover event |
| **Model Fallback** | 다른 model로 전환하는 품질 변경 | failover | `fallbacks` |
| **Usage** | token·request 같은 가격 계산 입력 | cost, charge | usage, tokens |
| **Charge** | Usage에 Price를 적용해 tenant에 귀속한 금액 | usage, provider cost | cost, billing |
| **Provider Cost** | upstream Provider Attempt로 회사가 부담하는 원가 | tenant charge | provider cost |
| **Budget** | 기간별 Charge 상한 | credit balance | hard limit, auto-pause |
| **Request Record** | 요청·상태·usage·latency를 보존한 불변 사실 | access log만 | logs |
| **Reconciliation** | 내부 원가와 provider 청구 내역을 대조하는 과정 | rating, 집계 | unified billing |
| **Data Plane** | client 요청이 실시간 통과하는 인증·routing·proxy 경로 | passthrough server | data plane |
| **Control Plane** | key·model·route·price를 관리하는 경로 | admin server | Management API |

## 헷갈리기 쉬운 관계

- Coding Agent와 Agent Host가 model/tool loop를 소유한다. Gateway는 loop를 소유하지 않는다.
- Model Gateway와 Tool Gateway는 서로 호출하지 않는다.
- Knowledge Base는 Internal Human AI·Internal Server AI와 독립적으로 구축할 수 있다.
- Retrieval API를 MCP READ tool로 감쌀 수 있지만 이는 Knowledge Base의 소비 adapter다.
- Tool Proposal은 Execution Decision 전까지 실행 권한이 없다.
- 하나의 Model Request가 retry·failover로 여러 Provider Attempt를 만들 수 있다.
- `Usage × Price → Charge`다. Budget은 기간 상한이고 credit은 선불 자산이다.
- OG의 `fallbacks`는 다른 model을 허용하므로 이 문서에서는 Model Fallback으로 해석한다.

## OG 공개 언어 출처

- [OpenGateway 개요](https://opengateway.ai/docs)
- [OpenGateway API Reference](https://opengateway.ai/docs/reference/guides/overview)
- [OpenGateway Errors](https://opengateway.ai/docs/reference/guides/errors)
- [OpenGateway Models](https://opengateway.ai/models)
