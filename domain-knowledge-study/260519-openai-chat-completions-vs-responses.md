# OpenAI API: Chat Completions vs Responses

## 1. 요약

### 1.1 TL;DR

- 새로 만든다면 기본 선택은 `Responses API`.
- 기존 `messages -> choices` 계약이 이미 굳어져 있으면 `Chat Completions` 유지.
- 단순 채팅만 하면 둘 다 가능하다.
- tool, MCP, built-in tools, reasoning, long-running task, conversation state가 중요하면 `Responses API`가 낫다.
- LLM gateway를 만든다면 두 API를 모두 이해하되, 내부 추상화는 `Responses`의 item/event 모델에 가깝게 잡는 편이 확장성이 좋다.

### 1.2 한 줄 정의

| API | 한 줄 정의 |
|---|---|
| `Chat Completions` | `messages[]`를 넣고 `choices[].message`를 받는 전통적인 채팅 생성 API |
| `Responses` | `input` item을 넣고 `output[]` item을 받는 통합 생성, reasoning, tool, state API |

### 1.3 핵심 차이

| 항목 | Chat Completions | Responses |
|---|---|---|
| 입력 모델 | message 중심 | typed item 중심 |
| 출력 모델 | choice/message 중심 | output item 중심 |
| 상태 관리 | 애플리케이션이 직접 messages 누적 | `previous_response_id`, `conversation` 사용 가능 |
| agent 기능 | 직접 orchestration 필요 | tool loop와 built-in tool이 API 모델에 자연스럽게 포함 |
| 새 기능 방향 | 호환성 유지 | 최신 기능 우선 적용 |

### 1.4 선택 기준

| 상황 | 선택 |
|---|---|
| 새 프로젝트 | `Responses` |
| 기존 OpenAI 호환 API 서버 | `Chat Completions` 유지 또는 adapter 제공 |
| Spring AI, LangChain 등 기존 예제 호환 | `Chat Completions`가 편함 |
| web/file/code/MCP tool agent | `Responses` |
| reasoning token, tool call, state를 한 trace로 보고 싶음 | `Responses` |

## 2. API 정체성 비교

| 관점 | Chat Completions | Responses |
|---|---|---|
| Endpoint | `POST /v1/chat/completions` | `POST /v1/responses` |
| 핵심 입력 | `messages[]` | `input`, `instructions`, `previous_response_id`, `conversation` |
| 핵심 출력 | `choices[].message` | `output[]`, SDK helper `output_text` |
| 대화 상태 | 클라이언트가 messages 누적 관리 | `previous_response_id` 또는 `conversation` 사용 가능 |
| 도구 호출 | 주로 function/custom tool | function, custom tool, built-in tools, MCP |
| Built-in tool | 제한적 또는 모델별 | web search, file search, computer use, code interpreter, image generation, MCP 등 |
| 멀티 생성 | `n`으로 여러 choice 생성 가능 | `n` 없음, 단일 response 중심 |
| 구조화 출력 | `response_format` | `text.format` |
| Reasoning | `reasoning_effort` | `reasoning.effort`, `reasoning.summary` 등 더 자연스러운 모델 |
| 운영 관점 | 레거시 호환, 단순 프록시 용이 | agentic workflow, 상태, 도구, observability에 유리 |

### 2.1 기능 지원 범위

읽는 법:

- `공통`: 둘 다 실무에서 쓸 수 있음
- `Chat 일부`: Chat Completions에서도 가능하지만 제한, 특수 모델, 다른 API shape가 있음
- `Responses 전용`: 일반적으로 Responses API를 써야 자연스럽거나 공식적으로 Responses 중심인 기능

### 2.2 공통 지원 기능

| 기능 | Chat Completions | Responses | 실무 메모 |
|---|---|---|---|
| Text generation | 지원 | 지원 | 가장 기본 기능 |
| Vision / image input | 지원 | 지원 | 모델별 지원 여부 확인 필요 |
| Streaming | 지원 | 지원 | 사용자 대면 채팅은 둘 다 SSE 처리 필요 |
| Function calling | 지원 | 지원 | Chat은 `tools`, Responses도 `tools`; 서버 검증은 동일하게 필수 |
| Structured Outputs | `response_format` | `text.format` | 기능은 공통, API shape는 다름 |
| Reasoning effort | `reasoning_effort` | `reasoning.effort` | Responses가 reasoning item/summary와 더 잘 연결됨 |
| Prompt caching | `prompt_cache_key`, `prompt_cache_retention` | 동일 계열 지원 | 긴 고정 prefix가 많을수록 중요 |
| Service tier | `service_tier` | `service_tier` | latency/cost SLA 라우팅 |
| Safety identifier | `safety_identifier` | `safety_identifier` | 사용자 식별자는 hash 사용 |
| Metadata / store | `metadata`, `store` | `metadata`, `store` | 저장 정책은 서비스 개인정보 정책과 같이 결정 |

### 2.3 Chat Completions에서도 일부 지원되는 기능

| 기능 | Chat 지원 방식 | 한계 / 차이 |
|---|---|---|
| Web search | `gpt-4o-search-preview`, `gpt-4o-mini-search-preview` 같은 search-preview 모델 | Responses의 일반 `web_search` built-in tool과 다르다. 모델 선택 자체가 search용으로 제한된다. |
| Audio input/output | `gpt-4o-audio-preview`, `gpt-4o-mini-audio-preview` 계열 + `modalities`, `audio` | 일반 Chat 모델 기능이 아니라 audio preview 모델 중심이다. Realtime/Audio API와도 구분 필요. |
| Multiple candidates | `n` | Responses에는 `n`이 없다. 후보 여러 개가 필요하면 여러 response 요청 또는 별도 rerank flow를 만든다. |
| Logprobs | `logprobs`, `top_logprobs` | Responses도 include로 output text logprobs를 받을 수 있지만, Chat의 request shape와 다르다. |
| Determinism hint | `seed ⚠️` + `system_fingerprint ⚠️` | best-effort이고 deprecated/beta 성격이다. 완전 재현성 보장 아님. |
| Compatibility gateway | `messages -> choices` | OpenAI-compatible API 서버, 기존 SDK, 프록시 구현에 유리하다. Responses는 adapter가 필요하다. |

### 2.4 Responses API 전용에 가까운 기능

| 기능 | 왜 Responses 쪽인가 | 서빙 개발자 관점 가치 |
|---|---|---|
| Built-in tool loop | OpenAI hosted tool을 `tools[]`로 구성 | 앱 서버가 모든 tool orchestration을 직접 만들 필요가 줄어든다. |
| MCP tools | remote MCP server와 connector를 tool로 연결 | 사내 도구, 문서 저장소, SaaS 연동을 표준 tool 인터페이스로 붙이기 좋다. |
| `previous_response_id` | 이전 response를 이어 multi-turn 생성 | 클라이언트가 전체 대화 messages를 계속 재구성하지 않아도 된다. |
| `conversation` | conversation에 input/output item 자동 누적 | 장기 세션, agent trace, user별 state 관리에 유리하다. |
| Typed `output[]` item | message, reasoning, function_call, tool call 결과가 item으로 분리 | agent runtime, replay, observability 구현이 쉬워진다. |
| Reasoning summary | `reasoning.summary` | 내부 reasoning 원문이 아니라 요약을 받아 디버깅 신호로 쓴다. |
| Encrypted reasoning | `include: ["reasoning.encrypted_content"]` | `store: false`나 zero data retention 환경에서도 reasoning context 연결에 도움. |
| Background mode | `background: true` | 오래 걸리는 research, file search, code interpreter 작업을 비동기로 처리. |
| Context management / compaction | `context_management` | 긴 대화나 agent 세션에서 context window 압박을 줄이는 운영 도구. |
| Tool result include | `include`로 file search 결과, web source, code interpreter output 등 포함 | 디버깅, citation, 감사 로그, UI 표시 근거 확보. |
| Responses-only models | API reference에 Responses-only 모델군 존재 | deep research, computer use, pro/codex 계열 등은 endpoint 선택에 직접 영향. |

### 2.5 결론

| 질문 | 답 |
|---|---|
| Chat Completions가 아직 쓸모 있나? | 있다. 호환성, 단순 챗, 기존 SDK/gateway에는 여전히 유용하다. |
| Chat에서도 최신 기능 일부가 되나? | 된다. structured output, function calling, streaming, reasoning effort, 일부 web/audio 모델은 가능하다. |
| 그래도 Responses가 필요한 이유는? | built-in tools, MCP, state, typed item, background, reasoning summary 등 agent 운영 기능 때문이다. |
| LLM 서빙 서버의 기본 추상화는? | 가능하면 `input item -> output item/event`에 가깝게 설계하고, Chat은 compatibility adapter로 다루는 편이 낫다. |

### 2.6 Migration guide 핵심

공식 migration guide 기준 요약:

- Responses API는 agent-like application을 만들기 위한 unified interface다.
- Built-in tools: web search, file search, computer use, code interpreter, remote MCP 등.
- Chat Completions는 `messages[] -> choices[].message` 모델이다.
- Responses는 `input/items -> output[]` 모델이다.
- Responses의 `item`은 message뿐 아니라 function call, function call output, reasoning, tool call 같은 여러 타입을 표현한다.
- Chat의 `n` 기반 multiple parallel generations는 Responses에 없다.
- Responses에는 `output_text` helper가 있다.
- Structured Outputs shape가 다르다: Chat `response_format`, Responses `text.format`.
- Function calling shape도 다르다: request의 function config와 response의 function call output 모두 변환이 필요하다.
- Multi-turn state는 Chat에서 직접 관리하지만, Responses는 `previous_response_id` 또는 Conversations API와 연결할 수 있다.
- `store: false`로 저장을 끌 수 있다.

opengateway adapter 관점:

| Migration 항목 | Adapter 처리 |
|---|---|
| endpoint | 외부 `/v1/chat/completions` 유지, 내부 `/v1/responses` 호출 가능 |
| `messages[]` | Responses `input` item 배열로 변환 |
| system/developer instruction | 가능하면 Responses `instructions`로 분리 |
| `choices[].message` | Responses `output[]`에서 assistant message를 추출해 Chat shape로 재포장 |
| `n` | 직접 대응 없음. `n > 1`은 다중 Responses 호출 또는 미지원 정책 필요 |
| `response_format` | `text.format`으로 변환 |
| `functions ⚠️`, `function_call ⚠️` | `tools`, `tool_choice`로 정규화 |
| `tool_calls` | Responses output item과 Chat tool call shape 간 mapping 필요 |
| conversation state | stateless compatibility면 매번 input 구성, stateful mode면 `previous_response_id` 검토 |
| streaming | Responses event stream을 Chat completion chunk stream으로 변환 |

## 3. Chat Completions API

### 3.1 무엇인가

요약:

- 목적: 대화 메시지 목록에서 assistant 응답 생성
- 입력 단위: `messages[]`
- 출력 단위: `choices[].message`
- 상태: 기본적으로 stateless
- 서버 책임: 이전 대화, tool 결과, RAG context를 매 요청에 다시 구성
- 강점: 호환성, 단순성, 기존 생태계
- 약점: agent state, tool trace, built-in tool 확장성이 약함

대표 요청:

```json
{
  "model": "gpt-5.4",
  "messages": [
    { "role": "developer", "content": "You are a helpful assistant." },
    { "role": "user", "content": "Hello!" }
  ]
}
```

대표 응답:

```json
{
  "id": "chatcmpl_...",
  "object": "chat.completion",
  "model": "gpt-5.4",
  "choices": [
    {
      "index": 0,
      "message": {
        "role": "assistant",
        "content": "Hello! How can I assist you today?"
      },
      "finish_reason": "stop"
    }
  ],
  "usage": {
    "prompt_tokens": 19,
    "completion_tokens": 10,
    "total_tokens": 29
  }
}
```

### 3.2 Request Fields

| Field | 가치 | 실전 사용법 |
|---|---|---|
| `model` | 품질, latency, 비용, context window를 결정하는 가장 큰 레버 | alias는 빠르게 시작할 때, snapshot은 재현성과 eval 안정성이 필요할 때 사용한다. |
| `messages` | 모델이 보는 전체 작업 문맥 | `developer` 또는 `system`에 정책/역할, `user`에 요청, `assistant/tool`에 이전 결과를 넣는다. 오래된 대화는 요약/검색/RAG로 줄인다. |
| `max_completion_tokens` | 출력 비용과 tail latency 방어 | visible token + reasoning token 상한이다. reasoning 모델에서 너무 낮게 잡으면 답변이 잘릴 수 있다. `max_tokens`는 deprecated다. |
| `temperature` | 샘플링 다양성 | 운영 서비스 기본은 낮게 둔다. 창작/브레인스토밍은 높인다. 일반적으로 `top_p`와 동시에 튜닝하지 않는다. |
| `top_p` | nucleus sampling | temperature 대신 확률 질량 기준으로 다양성을 제어한다. 실무에서는 둘 중 하나만 바꾼다. |
| `reasoning_effort` | reasoning 모델의 생각 예산 | `none/minimal/low/medium/high/xhigh` 계열. latency/cost와 정확도 사이의 명시적 trade-off다. |
| `response_format` | JSON/스키마 출력 강제 | API 서버 간 계약에는 `json_schema + strict`를 우선한다. 구 JSON mode인 `json_object`는 후순위다. |
| `tools` | 모델이 호출 가능한 함수/커스텀 도구 정의 | 모델이 직접 처리하면 위험하거나 비효율적인 작업을 서버 책임으로 분리한다. tool argument는 반드시 서버에서 검증한다. |
| `functions ⚠️` | 레거시 function 정의 | `tools`로 대체됐다. OpenAI-compatible adapter에서는 받을 수 있지만 내부에서는 `tools`로 정규화한다. |
| `tool_choice` | 도구 호출 정책 | `auto`는 모델 판단, `required`는 반드시 호출, 특정 tool 지정은 deterministic workflow에 유용하다. |
| `function_call ⚠️` | 레거시 function 호출 정책 | `tool_choice`로 대체됐다. 들어오면 `tool_choice`로 변환한다. |
| `parallel_tool_calls` | 여러 도구 병렬 호출 허용 | 독립 조회가 많은 agent flow에서는 켠다. 순서 의존 workflow는 끄거나 orchestration을 명확히 한다. |
| `n` | 한 요청에서 여러 후보 생성 | 품질 rerank나 비교에는 쓸 수 있지만 토큰 비용이 후보 수만큼 증가한다. 운영 기본은 `1`. |
| `stream` | first token latency 개선 | 사용자 대면 채팅은 거의 필수다. gateway에서는 SSE chunk 정규화와 취소 처리가 중요하다. |
| `stream_options.include_usage` | streaming에서도 usage 확보 | stream 종료 직전 usage chunk를 받는다. 중간 끊김 시 최종 usage가 없을 수 있어 fallback 계측이 필요하다. |
| `store` | 결과 저장 여부 | eval/distillation 활용이면 켠다. 민감 데이터/보존 정책이 있으면 명시적으로 끈다. |
| `metadata` | 추적/검색용 key-value | tenant, feature, experiment, route 같은 운영 메타를 붙인다. PII는 피한다. |
| `prompt_cache_key` | 유사 요청 캐시 히트 개선 | 동일 prefix가 많은 서빙, 예를 들어 고정 system prompt + 긴 정책 문서에서 효과가 크다. |
| `prompt_cache_retention` | 캐시 보존 시간 정책 | `24h`는 반복 traffic에서 비용/latency 개선 가능성이 있다. |
| `prediction` | 예상 출력이 거의 정해진 경우 latency 개선 | 코드/문서 일부 재생성처럼 대부분의 출력이 기존 파일과 같을 때 쓴다. |
| `modalities`, `audio` | audio output | Chat Completions에서 audio 모델을 쓸 때 필요하다. 일반 텍스트 챗에서는 제외한다. |
| `frequency_penalty` | 반복 억제 | 같은 문장/표현을 반복하는 요약/생성에서 조절한다. 과하면 핵심 용어 반복도 줄어든다. |
| `presence_penalty` | 새 주제 탐색 유도 | 브레인스토밍에는 유용하지만, QA/RAG에서는 답변이 산만해질 수 있다. |
| `logprobs`, `top_logprobs` | token-level 확률 관찰 | confidence, 분류, eval, 디버깅에 유용하다. user-facing 생성에서는 비용/응답 크기 증가를 고려한다. |
| `stop` | 특정 문자열에서 생성 중단 | 레거시 프롬프트 포맷에는 유용하지만 최신 reasoning 모델 일부에서 미지원이다. |
| `safety_identifier` | abuse 탐지용 안정 식별자 | 사용자 ID를 hash해서 넣는다. PII 원문을 넣지 않는다. |
| `user ⚠️` | 레거시 사용자 식별자 | `safety_identifier`와 `prompt_cache_key`로 대체 중이다. |
| `service_tier` | latency/cost SLA 선택 | 기본, flex, priority 등 운영 비용과 지연 요구에 맞춰 라우팅한다. |
| `seed ⚠️` | best-effort deterministic sampling | 완전 재현성은 보장되지 않는다. 회귀 테스트 목적이면 snapshot model과 별도 eval을 우선한다. |
| `max_tokens ⚠️` | 레거시 출력 token 상한 | `max_completion_tokens`로 대체됐다. o-series 모델과 호환되지 않는다. |

### 3.3 Response Fields

| Field | 가치 | 실전 사용법 |
|---|---|---|
| `id` | 요청 결과 식별자 | 장애 분석, retry, user report, audit log에 저장한다. |
| `choices[]` | 후보 응답 목록 | 보통 `choices[0]`만 사용한다. `n > 1`이면 rerank/선택 로직이 필요하다. |
| `choices[].message.content` | assistant 최종 텍스트 | 일반 채팅의 주 출력. tool call이면 null일 수 있다. |
| `choices[].message.tool_calls` | 모델이 요청한 도구 호출 | `id`, `name`, `arguments`를 서버에서 검증 후 실행하고, 결과를 `tool` message로 되돌린다. |
| `choices[].message.function_call ⚠️` | 레거시 function call 출력 | `tool_calls`로 대체됐다. adapter에서는 `tool_calls`로 정규화한다. |
| `choices[].finish_reason` | 중단 원인 | 정상 종료, token 부족, safety 차단, tool 호출 요청을 구분해 후속 처리를 결정한다. |
| `usage` | 비용/쿼터/최적화의 핵심 | tenant별 과금, route별 비용, prompt cache 효과, reasoning token 비중을 기록한다. |
| `service_tier` | 실제 적용된 처리 tier | 요청한 tier와 실제 tier가 다를 수 있으므로 응답값을 로깅한다. |
| `system_fingerprint ⚠️` | backend config 변화 추적 | `seed`와 함께 쓰던 determinism 보조 신호다. 현재는 deprecated로 표시되어 있다. |
| `logprobs` | 출력 token 확률 | confidence heuristic, classification threshold, token 선택 분석에 쓴다. |

## 4. Responses API

### 4.1 무엇인가

요약:

- 목적: 모델 응답 생성, reasoning, tool use, state 관리 통합
- 입력 단위: string 또는 typed `input[]` item
- 출력 단위: typed `output[]` item
- 상태: `previous_response_id`, `conversation` 사용 가능
- 서버 책임: tool 실행, 권한 검증, trace 저장, 비용 제어
- 강점: agent workflow, built-in tools, MCP, multimodal, background 작업
- 약점: `messages -> choices` 호환 레이어가 필요한 기존 시스템에서는 adapter가 필요

대표 요청:

```json
{
  "model": "gpt-5.4",
  "instructions": "You are a helpful assistant.",
  "input": "Hello!"
}
```

대표 응답:

```json
{
  "id": "resp_...",
  "object": "response",
  "status": "completed",
  "model": "gpt-5.4",
  "output": [
    {
      "type": "message",
      "role": "assistant",
      "content": [
        { "type": "output_text", "text": "Hello! How can I assist you today?" }
      ]
    }
  ],
  "output_text": "Hello! How can I assist you today?",
  "usage": {
    "input_tokens": 37,
    "output_tokens": 11,
    "total_tokens": 48
  }
}
```

### 4.2 Request Fields

| Field | 가치 | 실전 사용법 |
|---|---|---|
| `model` | 모델 선택 | 최신 기능은 Responses에서 더 자연스럽게 붙는다. 모델별 tool/reasoning/audio 지원 차이를 라우팅 테이블로 관리한다. |
| `input` | 사용자 입력 및 context item | 문자열로 빠르게 시작하거나, `input_text`, `input_image`, `input_file`, tool output 등 typed item으로 정교하게 구성한다. |
| `instructions` | system/developer 지시 | `previous_response_id`와 함께 써도 이전 instructions가 자동 계승되지 않는다. 턴별 정책 교체가 쉬워진다. |
| `previous_response_id` | 간단한 multi-turn chaining | 서버가 전체 messages를 재구성하지 않아도 이전 response에 이어갈 수 있다. `conversation`과 동시에 쓰면 안 된다. |
| `conversation` | persistent conversation state | conversation에 input/output item이 누적된다. 사용자별 장기 세션, agent trace에 적합하다. |
| `store` | 응답 저장/조회 여부 | 기본 저장 동작과 데이터 정책을 명확히 정해야 한다. stateless/민감 데이터면 `false`를 검토한다. |
| `include` | 응답에 추가 artifact 포함 | 기본 응답에는 없는 디버깅/출처/중간 산출물을 필요할 때만 포함한다. 응답 크기와 보안 면을 같이 본다. |
| `reasoning.effort` | reasoning budget | Chat의 `reasoning_effort`보다 구조적으로 명확하다. 난이도 기반 라우팅에 핵심이다. |
| `reasoning.summary` | reasoning 요약 | 내부 reasoning 원문이 아니라 요약을 받아 디버깅/관찰성에 활용한다. |
| `reasoning.generate_summary ⚠️` | 레거시 reasoning 요약 옵션 | `reasoning.summary`로 대체됐다. |
| `text.format` | 출력 포맷 | `json_schema + strict`로 downstream parser 안정성을 높인다. Responses에서는 Chat의 `response_format`이 아니라 `text.format`이다. |
| `text.verbosity` | 응답 장황함 제어 | 같은 prompt를 유지하면서 concise/verbose UX를 만들 때 유용하다. |
| `tools` | built-in/MCP/function/custom 도구 | Responses의 핵심. web search, file search, code interpreter, MCP, function call을 한 요청 안에서 agent loop로 사용할 수 있다. |
| `tool_choice` | tool selection 제어 | `none/auto/required`, 특정 hosted tool/function/MCP/custom tool 강제, allowed tools 제약이 가능하다. |
| `max_tool_calls` | tool loop 폭주 방지 | built-in tool call 총량 상한이다. agent 비용과 latency를 방어한다. |
| `parallel_tool_calls` | 병렬 tool call 허용 | 독립 도구 조회를 병렬화해 latency를 줄인다. 사이드이펙트 있는 tool에는 주의한다. |
| `background` | 장기 작업 비동기 실행 | deep research, file search-heavy, code interpreter-heavy 요청처럼 오래 걸리는 작업에 사용한다. |
| `stream` | SSE streaming | UI latency를 줄이고 tool/reasoning/message event를 점진적으로 처리한다. |
| `stream_options.include_obfuscation` | stream side-channel 완화 | 기본 보안성과 bandwidth 사이의 선택이다. 신뢰된 내부망이면 끄는 것을 검토할 수 있다. |
| `max_output_tokens` | 출력 + reasoning token 상한 | reasoning token도 포함한다. reasoning 모델에서 너무 낮으면 `incomplete`가 될 수 있다. |
| `temperature`, `top_p` | sampling 제어 | Chat과 동일하게 둘 중 하나 중심으로 튜닝한다. |
| `prompt` | 저장된 prompt template 사용 | prompt versioning/variables를 플랫폼 쪽으로 옮길 수 있다. 실험과 배포 관리에 유리하다. |
| `prompt_cache_key`, `prompt_cache_retention` | prompt cache 최적화 | 반복 prefix가 큰 서비스에서 비용/latency 개선에 중요하다. Responses는 cache 활용 개선 이점이 강조된다. |
| `context_management` | compaction 설정 | 긴 context에서 compact threshold를 통해 token budget을 관리한다. agent 장기 세션에 중요하다. |
| `metadata` | 운영 추적 | route, tenant, experiment, request class를 붙여 dashboard/API 조회에 활용한다. |
| `safety_identifier` | abuse 탐지용 사용자 식별 | hash 기반 안정 ID를 넣는다. |
| `user ⚠️` | 레거시 사용자 식별자 | `safety_identifier`와 `prompt_cache_key`로 대체 중이다. |
| `service_tier` | 처리 tier | priority/flex/default 라우팅으로 latency와 비용을 분리한다. |

### 4.3 Response Fields

| Field | 가치 | 실전 사용법 |
|---|---|---|
| `id` | response chain의 anchor | `previous_response_id`로 다음 턴에 연결할 수 있다. |
| `status` | lifecycle 상태 | 완료, 진행 중, 미완료, 실패를 구분해 polling/retry/cancel 정책을 나눈다. |
| `output[]` | typed output item 목록 | 모델 실행 중 생긴 결과 이벤트 목록이다. agent runtime은 이 배열을 event log처럼 처리한다. |
| `output_text` | SDK/helper 성격의 최종 텍스트 | 단순 텍스트 응답에서는 편하지만, tool/reasoning/multimodal item을 무시하지 않도록 원본 `output[]`도 저장한다. |
| `error` | 실패 상세 | retry 가능 오류와 user-visible 오류를 나눈다. |
| `incomplete_details` | 미완료 이유 | token 상한, safety 차단 같은 원인을 확인해 재요청 또는 사용자 응답 정책을 정한다. |
| `usage.input_tokens` | 입력 비용 | prompt cache, RAG chunk 크기, conversation 누적 비용을 본다. |
| `usage.output_tokens` | 출력 비용 | visible output + reasoning 관련 비용 분석에 필요하다. |
| `usage.output_tokens_details.reasoning_tokens` | reasoning 비용 | reasoning effort 튜닝의 핵심 지표다. |
| `parallel_tool_calls`, `tools`, `tool_choice` | 실행 설정 반영 | 요청값과 실제 응답 설정을 함께 로깅해 agent 문제를 재현한다. |
| `previous_response_id`, `conversation` | state lineage | 대화 추적, audit, replay에 중요하다. |
| `reasoning` | reasoning 설정/요약 | reasoning summary를 켰다면 디버깅 근거로 남긴다. |
| `metadata`, `service_tier` | 운영 분석 | 비용/latency dashboard의 dimension으로 쓴다. |

## 5. 같은 개념, 다른 필드

| 개념 | Chat Completions | Responses | 메모 |
|---|---|---|---|
| 대화 입력 | `messages[]` | `input[]` 또는 string | Responses item은 message, tool call output, file/image input까지 typed로 표현한다. |
| 시스템 지시 | `developer/system message` | `instructions` | Responses에서는 지시와 사용자 입력이 분리되어 prompt 교체가 쉽다. |
| 최종 텍스트 | `choices[0].message.content` | `output_text` 또는 `output[].content[]` | Responses는 item stream을 먼저 보고, convenience로 `output_text`를 사용한다. |
| 여러 후보 | `n` | 없음 | Responses는 단일 response 중심이다. 후보 생성은 여러 요청 또는 별도 rerank flow로 구성한다. |
| 구조화 출력 | `response_format` | `text.format` | 둘 다 JSON Schema strict를 우선한다. |
| reasoning 제어 | `reasoning_effort` | `reasoning.effort` | Responses가 reasoning item/summary와 더 잘 맞는다. |
| tool 정의 | `tools[].function` | `tools[]` | Responses는 built-in/MCP/function/custom이 같은 배열에 들어간다. |
| tool 결과 | `role: "tool"` message | function/custom tool output item | Responses가 tool loop event를 더 명확하게 표현한다. |
| 상태 연결 | 직접 messages 재전송 | `previous_response_id`, `conversation` | LLM gateway 구현 난이도와 token 사용량에 직접 영향. |
| 저장 | `store` | `store` | 목적과 개인정보 정책에 따라 명시적으로 세팅한다. |

## 6. Key features 정리

### Chat Completions key features

- 가장 널리 쓰인 `messages -> choices` 계약이다.
- 기존 SDK, LangChain/Spring AI/프록시/Gateway 통합 예제가 많다.
- function calling, structured outputs, streaming, vision/audio 모델 일부를 지원한다.
- `n`, `logprobs`, `seed`, `stop` 같은 전통적 completion 제어 파라미터에 익숙한 팀에 적합하다.
- 단점은 상태 관리와 agent loop orchestration을 대부분 애플리케이션이 직접 해야 한다는 점이다.

### Responses key features

- 새 프로젝트 권장 API이며, Chat Completions의 상위 개념에 가깝다.
- text/image/file input, structured output, reasoning, built-in tools, function tools, MCP tools를 한 모델로 다룬다.
- `previous_response_id`, `conversation`, `store`로 stateful interaction을 쉽게 만들 수 있다.
- `output[]` typed item이 message와 tool call을 분리해 agent runtime 구현에 유리하다.
- background mode, context compaction, built-in tool include 결과, reasoning summary처럼 운영형 agent 기능이 풍부하다.

## 7. LLM 서빙 개발자 관점의 설계 원칙

### 7.1 라우팅

핵심:

- `model`만으로 라우팅하지 않는다.
- 작업 유형, latency, tool 필요성, reasoning 필요성을 함께 본다.
- 라우팅 결과는 비용/품질 분석을 위해 반드시 로깅한다.

라우팅 dimension:

| Dimension | 예 |
|---|---|
| 작업 유형 | chat, RAG, extraction, coding, long reasoning, tool agent |
| 품질/latency 목표 | realtime, interactive, async, batch |
| 입력 modality | text, image, file, audio |
| 도구 필요성 | none, function, web, file, code, MCP |
| reasoning 필요성 | none/low/medium/high |
| 비용 정책 | default, flex, priority, cached prefix |

### 7.2 비용 계측

반드시 저장할 값:

- request id: `x-request-id`, 가능하면 `X-Client-Request-Id`
- API: chat vs responses
- model, snapshot/alias
- input/output/reasoning/cached token
- service tier
- tool call 횟수와 종류
- finish/status/incomplete reason
- latency: queue, first token, completion, tool latency
- tenant/user hash, feature, experiment metadata

### 7.3 tool call 안전성

핵심:

- tool arguments는 모델 출력이다.
- 모델 출력은 신뢰 경계 밖 데이터다.
- JSON처럼 보여도 invalid JSON일 수 있다.
- schema 밖 필드가 hallucinate될 수 있다.

서버에서 강제할 것:

- JSON schema validation
- allowlist 기반 tool 이름 검증
- side-effect tool은 idempotency key 요구
- 권한 검증은 모델이 아니라 서버가 수행
- tool timeout, max call count, retry budget 설정
- tool output을 그대로 사용자에게 노출하지 않고 후처리

### 7.4 structured output

핵심:

- "JSON으로 답해"만으로는 부족하다.
- 서버 계약이 있으면 schema를 건다.
- schema version을 함께 저장한다.

우선순위:

1. `json_schema` + `strict`
2. schema는 있지만 strict off
3. legacy `json_object`
4. free-form text 후 서버 parser

운영 체크:

- schema name
- schema version
- model
- API type
- parsing success/failure
- raw response 보존 정책

### 7.5 conversation state

Chat Completions:

- 상태 위치: 애플리케이션 서버
- 장점: 단순함, 이식성, vendor lock-in 감소
- 비용: 매 요청마다 context 재구성
- 필요한 정책: truncation, summary, RAG, token budget

Responses:

- 상태 위치: OpenAI response/conversation primitive 활용 가능
- 장점: stateful UX 구현 쉬움
- 비용: 저장 정책과 삭제 정책을 더 명확히 해야 함
- 필요한 정책: PII, retention, replay, audit, user deletion

## 8. 언제 무엇을 쓸까

| 상황 | 추천 |
|---|---|
| 새 OpenAI 기반 제품 | Responses |
| 기존 Chat Completions 호환 SDK/gateway 유지 | Chat Completions 유지 또는 compatibility adapter |
| function calling만 필요한 단순 챗봇 | 둘 다 가능, 새 개발이면 Responses |
| web/file/code/MCP built-in tool agent | Responses |
| multi-turn state를 서버에서 쉽게 이어가고 싶음 | Responses |
| 여러 후보 생성 후 rerank | Chat의 `n` 또는 Responses 다중 요청 |
| deterministic regression/eval | snapshot model + 낮은 sampling + usage/system fingerprint 로깅 |
| strict JSON API 응답 | Chat `response_format`, Responses `text.format` |

## 9. opengateway 구현 베스트 프랙티스

### 9.1 기본 방향

권장 구조:

| Layer | 역할 |
|---|---|
| Public API | `/v1/chat/completions` 호환 endpoint 제공 |
| Adapter | Chat request를 내부 canonical request로 변환 |
| Canonical model | Responses의 `input item -> output item/event` 모델에 가깝게 설계 |
| Provider client | 기본적으로 Responses API 호출 |
| Response mapper | Responses 결과를 Chat `choices[]` shape로 재포장 |

핵심 판단:

- 외부 호환성은 Chat Completions shape로 제공한다.
- 내부 실행 모델은 Responses shape에 맞춘다.
- 변환 불가능하거나 의미가 달라지는 필드는 명시적으로 정책화한다.
- adapter는 "그냥 프록시"가 아니라 "호환성 계층"이다.

### 9.2 Chat request 수신 시 처리 순서

1. Request validation
2. Deprecated field normalization
3. Chat `messages[]` 분석
4. Responses `instructions`와 `input` 구성
5. Tool schema 변환
6. Provider routing
7. Responses API 호출
8. Responses `output[]` 해석
9. Chat `choices[]` 응답으로 mapping
10. Usage, latency, tool trace 저장

### 9.3 Field normalization

| 들어온 필드 | 처리 |
|---|---|
| `functions ⚠️` | `tools`로 변환 |
| `function_call ⚠️` | `tool_choice`로 변환 |
| `max_tokens ⚠️` | `max_completion_tokens`로 변환 후 Responses 호출 시 `max_output_tokens`로 변환 |
| `user ⚠️` | 가능하면 `safety_identifier`로 변환. 원본 PII 저장 금지 |
| `seed ⚠️` | 기본 미지원 또는 best-effort metadata로만 보관 |
| `response_format` | Responses `text.format`으로 변환 |
| `reasoning_effort` | Responses `reasoning.effort`로 변환 |

정책:

- deprecated field를 조용히 무시하지 않는다.
- 변환한 필드는 trace에 남긴다.
- 의미 보존이 안 되는 필드는 400, degrade, ignore 중 하나를 명시적으로 선택한다.

### 9.4 `messages[]` 변환 전략

| Chat message | Responses 변환 |
|---|---|
| `role: developer` | 우선 `instructions`로 이동 |
| `role: system` | `instructions`에 병합하거나 input message로 유지 |
| `role: user` | `input` message item |
| `role: assistant` | 이전 assistant message item |
| `role: tool` | tool result item |

주의:

- `developer`와 `system`이 동시에 있으면 병합 순서를 정한다.
- 기존 Chat 호환성을 우선하면 원래 순서를 최대한 보존한다.
- Responses의 `instructions`는 `previous_response_id` 사용 시 자동 상속되지 않는다는 점을 고려한다.
- 장기 대화는 무조건 전체 messages를 넘기지 말고 summary/RAG/token budget 정책을 둔다.

### 9.5 `n` 처리

Responses에는 Chat의 `n`과 직접 대응되는 필드가 없다.

권장 정책:

| `n` 값 | 처리 |
|---|---|
| 없음 또는 `1` | 단일 Responses 호출 |
| `2+` | 여러 Responses 호출 후 `choices[]`로 합치기 |
| 비용 제한 서비스 | `n > 1` 미지원으로 400 반환 |

운영 관점:

- `n > 1`은 비용이 선형 증가한다.
- streaming에서 `n > 1`을 완전 호환하려면 chunk multiplexing이 필요해 복잡도가 커진다.
- 대부분의 프로덕션 chat endpoint는 `n = 1`만 허용해도 충분하다.

### 9.6 Tool call mapping

Chat shape:

```json
{
  "choices": [
    {
      "message": {
        "tool_calls": [
          {
            "id": "call_...",
            "type": "function",
            "function": {
              "name": "search",
              "arguments": "{\"query\":\"...\"}"
            }
          }
        ]
      }
    }
  ]
}
```

Responses shape:

```json
{
  "output": [
    {
      "type": "function_call",
      "call_id": "call_...",
      "name": "search",
      "arguments": "{\"query\":\"...\"}"
    }
  ]
}
```

Mapping 원칙:

- `call_id`와 Chat `tool_call.id`를 안정적으로 연결한다.
- arguments는 string이어도 JSON validation을 다시 수행한다.
- side-effect tool은 idempotency key를 요구한다.
- tool 실행 권한은 모델이 아니라 서버가 검사한다.
- parallel tool call은 순서 의존성이 없는 경우에만 허용한다.

### 9.7 Streaming mapping

난이도:

- non-streaming mapping보다 streaming mapping이 훨씬 어렵다.
- Responses는 event stream이고, Chat은 `chat.completion.chunk` shape를 기대한다.

권장 단계:

1. non-streaming adapter 먼저 구현
2. 텍스트 delta streaming 구현
3. tool call delta streaming 구현
4. usage final chunk 구현
5. cancel/timeout 처리

반드시 정할 것:

- client disconnect 시 provider request cancel 여부
- final usage chunk 누락 시 추정/미기록 정책
- tool call 중 stream 중단 시 재시도 여부
- partial output 저장 여부

### 9.8 Usage and observability

반드시 저장:

| 항목 | 이유 |
|---|---|
| public request id | 사용자 문의, 장애 추적 |
| provider response id | OpenAI 로그 추적 |
| external API shape | chat compatibility 요청인지 responses 직접 요청인지 구분 |
| internal route | 어떤 adapter/provider/model로 갔는지 확인 |
| model | 비용/품질 분석 |
| token usage | 과금/쿼터/최적화 |
| cached token | prompt cache 효과 분석 |
| reasoning token | reasoning effort 튜닝 |
| service tier | latency/cost SLA 분석 |
| tool calls | agent 비용과 실패 원인 분석 |
| finish/status reason | retry/degrade 판단 |
| first token latency | UX 지표 |
| total latency | SLA 지표 |

### 9.9 Error handling

| 상황 | 처리 |
|---|---|
| invalid JSON arguments | tool 실행 금지, validation error |
| unsupported Chat field | 400 또는 documented ignore |
| Responses `incomplete` | 원인에 따라 Chat `finish_reason`으로 변환 |
| content filter | user-visible safety response 또는 error mapping |
| provider timeout | retry 가능 여부 판단 후 gateway timeout |
| rate limit | retry-after 전달 또는 fallback |
| tool timeout | tool error item으로 모델에 반환하거나 요청 실패 |

원칙:

- 모델 출력 오류와 provider 오류를 구분한다.
- retry는 idempotent 요청에만 안전하다.
- side-effect tool 이후 retry는 별도 idempotency 설계 없이는 위험하다.

### 9.10 Recommended defaults

| 항목 | 기본값 |
|---|---|
| API strategy | public Chat-compatible, internal Responses-first |
| `n` | `1`만 지원 |
| `temperature` | 서비스 기본 낮게 유지 |
| `top_p` | 기본값 유지. `temperature`와 동시 튜닝 금지 |
| structured output | `json_schema + strict` 우선 |
| deprecated fields | normalize 후 trace 기록 |
| tool arguments | 항상 JSON schema validation |
| metadata | tenant, feature, route, experiment만. PII 금지 |
| safety id | hash 기반 `safety_identifier` |
| streaming | non-streaming 안정화 후 도입 |

## 10. 학습 순서

1. Chat Completions로 `messages`, role, streaming, usage를 정확히 익힌다.
2. function calling을 구현하고 tool argument validation을 붙인다.
3. Structured Outputs를 `json_schema + strict`로 구현한다.
4. 같은 기능을 Responses로 옮기며 `input`, `output[]`, `output_text`, `previous_response_id` 차이를 본다.
5. Responses built-in tools: web search, file search, code interpreter, MCP 순으로 실험한다.
6. reasoning effort별 latency/cost/quality를 작은 eval set으로 측정한다.
7. prompt cache, service tier, background mode, conversation state를 운영 관점에서 실험한다.

## 11. 참고 공식 문서

- OpenAI API Reference: Create chat completion  
  https://developers.openai.com/api/reference/resources/chat/subresources/completions/methods/create
- OpenAI API Reference: Create a model response  
  https://developers.openai.com/api/reference/resources/responses/methods/create
- OpenAI Guide: Migrate to the Responses API  
  https://developers.openai.com/api/docs/guides/migrate-to-responses
- OpenAI Models Guide  
  https://developers.openai.com/api/docs/models
- OpenAI Model: GPT-4o Search Preview  
  https://developers.openai.com/api/docs/models/gpt-4o-search-preview
- OpenAI Model: GPT-4o Audio Preview  
  https://developers.openai.com/api/docs/models/gpt-4o-audio-preview
