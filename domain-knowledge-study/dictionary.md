# Domain Knowledge Dictionary

## OpenAI API

| 용어 | 뜻 |
|---|---|
| `background` | 오래 걸리는 response를 비동기로 실행하는 방식. research, file search, code interpreter처럼 긴 작업에 사용한다. |
| Built-in tool | OpenAI가 제공하는 내장 도구. |
| Chat Completions | `messages[]`를 입력으로 받고 `choices[]`를 출력하는 OpenAI의 채팅 생성 API. 기존 SDK, gateway, OpenAI-compatible API에서 많이 쓰인다. |
| `choice` | Chat Completions에서 모델이 생성한 후보 응답 하나. 보통 `choices[0]`만 사용하지만 `n > 1`이면 여러 후보가 생긴다. |
| `conversation` | Responses의 지속 대화 상태. multi-turn state를 API primitive로 관리한다. |
| `finish_reason` | Chat Completions에서 생성이 끝난 이유. 값에 따라 후속 처리, retry, tool 실행 여부가 달라진다. |
| `finish_reason.content_filter` | safety/content filter로 출력이 제한된 상태. 사용자 응답, 로깅, 재시도 정책을 분리해야 한다. |
| `finish_reason.length` | token 상한에 걸려 응답이 잘린 상태. max token 조정이나 재요청 정책이 필요하다. |
| `finish_reason.stop` | 모델이 정상적으로 응답 생성을 끝낸 상태. 일반적인 성공 종료로 본다. |
| `finish_reason.tool_calls` | 모델이 tool 호출을 요청하고 생성을 멈춘 상태. 서버가 tool을 실행한 뒤 결과를 다시 모델에 전달해야 한다. |
| Function calling | 모델이 JSON arguments로 서버 측 함수를 호출하게 하는 방식. `tool.function` 계열로 보는 것이 좋고, arguments는 반드시 서버에서 검증해야 한다. |
| `function_call` ⚠️ | Chat Completions의 레거시 function call 제어/출력 필드. `tool_choice`, `tool_calls`로 대체됐다. |
| `functions` ⚠️ | Chat Completions의 레거시 function 목록 필드. `tools`로 대체됐다. |
| Image API | 이미지 생성/편집 전용 API. 단일 이미지 생성이나 편집 작업에는 Responses보다 직접적일 수 있다. |
| `incomplete.details` | Responses가 미완료된 이유. token 부족, content filter 등 원인을 보고 재시도 전략을 정한다. |
| `item` | Responses API의 typed 입출력 단위. 모델 실행 중 생긴 사건을 event log처럼 표현한다. |
| `item.function_call` | Responses에서 모델이 서버 측 function 실행을 요청하는 item. Chat의 `tool_calls`와 mapping 대상이 된다. |
| `item.message` | Responses에서 대화 message를 표현하는 item. Chat의 message와 가장 가까운 개념이다. |
| `item.reasoning` | Responses에서 모델의 reasoning 관련 정보를 표현하는 item. reasoning summary나 reasoning trace 관찰에 연결된다. |
| `item.tool_result` | tool 실행 결과를 Responses 흐름에 되돌려주는 item. tool loop를 이어가기 위한 연결점이다. |
| JSON Schema | 출력 JSON 구조를 정의하는 schema. `schema.strict`와 함께 prompt보다 강한 출력 계약을 만든다. |
| `logprobs` | 출력 token별 log probability를 반환하는 옵션 또는 결과 필드. token likelihood, confidence threshold, 후보 ranking, RAG self-eval, perplexity 계산에 쓴다. 값은 보통 0 이하이고 0에 가까울수록 해당 token 가능성이 높다. |
| `max_tokens` ⚠️ | 레거시 출력 token 상한. `max_completion_tokens`로 대체됐다. |
| MCP | Model Context Protocol. 외부 도구나 서비스, 사내 시스템을 모델이 사용할 수 있는 표준 tool 인터페이스로 연결한다. |
| `message` | role과 content를 가진 대화 단위. Chat Completions의 기본 자료구조이고, Responses에서는 typed item 중 하나로 표현된다. |
| `metadata` | 요청/응답에 붙이는 key-value. tenant, feature, experiment 추적에 쓴다. PII는 넣지 않는다. |
| `metadata.experiment` | 실험군이나 prompt/model variant를 나타내는 값. A/B test와 eval 분석에 사용한다. |
| `metadata.feature` | 어떤 기능에서 발생한 요청인지 나타내는 값. 기능별 비용/품질 분석에 사용한다. |
| `metadata.tenant` | tenant나 customer 구분자. 비용, quota, 장애 영향 범위 분석에 사용한다. |
| `output.text` | Responses output 중 텍스트만 편하게 모은 helper 값. 단순 채팅에는 편하지만 agent trace는 `output[]`를 봐야 한다. |
| `previous_response_id` | 이전 response에 이어서 생성하기 위한 참조 id. messages 전체를 다시 만들지 않고 다음 턴을 이어갈 수 있다. |
| `prompt_cache` | 반복되는 prompt prefix를 캐시하는 기능. 긴 고정 instruction이나 RAG prefix가 있을 때 latency와 비용을 줄인다. |
| `prompt_cache.key` | 유사한 prompt prefix를 같은 cache bucket으로 묶는 key. tenant, route, prompt version 단위로 설계한다. |
| `prompt_cache.retention` | prompt cache를 얼마나 유지할지 정하는 정책. 반복 traffic이 많을수록 비용/latency 최적화에 중요하다. |
| `reasoning` | 모델이 답을 만들기 위해 사용하는 내부 추론 과정 또는 예산. |
| `reasoning.effort` | reasoning 예산 수준. 낮추면 빠르고 싸지만 어려운 문제 품질이 떨어질 수 있다. |
| `reasoning.generate_summary` ⚠️ | reasoning summary를 요청하던 레거시 옵션. `reasoning.summary`로 대체됐다. |
| `reasoning.summary` | 내부 reasoning의 요약. 원문 reasoning이 아니라 디버깅용 신호로 본다. |
| `reasoning.token` | 내부 추론에 사용되는 token. 사용자에게 보이지 않아도 비용과 token budget을 사용한다. |
| Responses | `input`을 받아 `output[]` item을 반환하는 OpenAI의 통합 응답 API. text, reasoning, tool use, multimodal, stateful workflow를 더 자연스럽게 다룬다. |
| `role` | message의 발화자나 기능을 구분하는 필드. |
| `role.assistant` | 모델이 생성한 응답 역할. 다음 턴의 대화 이력으로 다시 넣을 수 있다. |
| `role.developer` | 개발자가 모델에 주는 상위 지시 역할. 서비스 정책, 출력 형식, 역할 정의를 넣는다. |
| `role.system` | 시스템 수준 지시 역할. 모델/버전별로 `role.developer`와 쓰임이 겹칠 수 있다. |
| `role.tool` | 외부 도구 실행 결과를 모델에 돌려주는 역할. tool calling loop에서 사용한다. |
| `role.user` | 사용자의 입력을 나타내는 역할. 실제 요청, 질문, 지시가 여기에 들어간다. |
| `safety_identifier` | abuse 탐지를 위한 사용자 식별자. 원본 user id 대신 hash를 넣는 것이 좋다. |
| `schema.strict` | schema를 더 엄격히 따르게 하는 설정. API 응답을 기계적으로 파싱할 때 우선 사용한다. |
| `seed` ⚠️ | best-effort deterministic sampling 힌트. 공식 문서상 deprecated이며 완전 재현성은 보장되지 않는다. |
| `service_tier` | 요청 처리 tier. latency와 비용 SLA를 라우팅한다. |
| `service_tier.default` | 기본 처리 tier. 특별한 latency/cost 요구가 없을 때 사용한다. |
| `service_tier.flex` | 비용을 낮추는 대신 latency 변동을 더 허용하는 tier. batch성 작업에 적합하다. |
| `service_tier.priority` | 더 높은 처리 우선순위를 기대하는 tier. 사용자 대면 고가치 요청에 적합하다. |
| `status` | Responses 객체의 lifecycle 상태. polling, retry, 사용자 응답 변환의 기준이 된다. |
| `status.completed` | Responses 처리가 정상 완료된 상태. output을 읽어 사용자 응답으로 변환한다. |
| `status.failed` | Responses 처리 자체가 실패한 상태. retry 가능 오류와 사용자 노출 오류를 분리한다. |
| `status.in_progress` | Responses 처리가 아직 진행 중인 상태. background mode나 polling에서 중요하다. |
| `status.incomplete` | Responses가 완료되지 못한 상태. `incomplete.details`를 보고 token 부족, filter 등 원인을 판단한다. |
| `streaming` | 응답을 chunk/event로 받는 방식. first token latency와 사용자 경험에 중요하다. |
| `streaming.cancel` | client disconnect나 timeout 시 provider 요청을 중단하는 처리. 비용 절감과 리소스 보호에 중요하다. |
| `streaming.delta` | 생성 중인 텍스트나 tool call 정보를 조각 단위로 받는 이벤트. UI 표시와 chunk mapping에 사용한다. |
| `streaming.usage` | streaming 종료 시점에 받는 token 사용량 정보. 중간 끊김 시 누락될 수 있어 fallback 계측이 필요하다. |
| Structured output | schema에 맞춰 JSON을 생성하도록 강제하는 방식. 서버 간 계약, parser 안정성, 자동화에 중요하다. |
| `system_fingerprint` ⚠️ | backend config fingerprint. `seed`와 함께 쓰던 determinism 보조 신호지만 deprecated다. |
| Tool call | 모델이 외부 기능 실행을 요청하는 이벤트. |
| `tool.call` | 모델이 실제로 tool 실행을 요청한 이벤트. 서버는 arguments를 검증한 뒤 실행해야 한다. |
| `tool.choice` | 모델이 tool을 쓸지, 어떤 tool을 쓸지 제어하는 정책. workflow 결정성을 높일 때 중요하다. |
| `tool.code_interpreter` | OpenAI built-in code execution tool. 계산, 데이터 분석, 파일 처리에 사용한다. |
| `tool.computer_use` | OpenAI built-in computer control tool. UI 조작이나 브라우저/데스크톱 작업 자동화에 사용한다. |
| `tool.definition` | 모델이 사용할 수 있는 tool의 이름, 설명, 입력 schema를 정의한 것. 좋은 tool description은 호출 품질에 직접 영향을 준다. |
| `tool.file_search` | OpenAI built-in file search tool. 업로드된 문서나 vector store 기반 검색에 사용한다. |
| `tool.function` | 개발자가 직접 정의하는 서버 측 function tool. DB 조회, 내부 API 호출, 계산 등에 사용한다. |
| `tool.image_generation` | OpenAI built-in image generation tool. 대화형 이미지 생성이나 multi-step 이미지 작업에 사용한다. |
| `tool.result` | 서버가 tool 실행 결과를 모델에 돌려주는 데이터. 너무 길거나 비정형이면 다음 응답 품질이 떨어진다. |
| `tool.web_search` | OpenAI built-in web search tool. 최신 정보 검색이나 citation이 필요한 흐름에 사용한다. |
| `top_logprobs` | 각 token position에서 가능성이 높은 후보 token과 log probability를 몇 개까지 함께 받을지 정하는 옵션. `logprobs`를 켰을 때 alternative token 분석에 사용한다. |
| `usage` | token 사용량. 비용, quota, 최적화, 과금의 기본 데이터다. |
| `usage.completion_tokens` | 모델 출력에 사용된 token. Chat Completions에서 주로 쓰는 표현이다. |
| `usage.input_tokens` | Responses 입력에 사용된 token. input item, instructions, conversation context 비용을 본다. |
| `usage.output_tokens` | Responses 출력에 사용된 token. visible text, reasoning, tool call 생성 비용을 본다. |
| `usage.prompt_tokens` | 입력 context에 사용된 token. Chat Completions에서 주로 쓰는 표현이다. |
| `user` ⚠️ | 레거시 사용자 식별자. `safety_identifier`, `prompt_cache_key`로 대체 중이다. |

## 샘플링 / 디코딩 파라미터

다음 토큰을 고를 때 확률 분포를 어떻게 자르고 고를지 제어하는 값들. 모델 출력의 다양성↔일관성을 조절한다. 토큰 생성(decode) 단계에 적용된다.

| 용어 | 뜻 |
|---|---|
| `temperature` | logits를 나누는 값으로 확률 분포의 뾰족함을 조절한다. 낮으면(0~0.3) 결정적·일관적, 높으면(0.8~1.2) 다양·창의적. 0이면 사실상 greedy. RAG·추출·코드엔 낮게, 창작엔 높게. |
| `top_k` | 확률 상위 `k`개 토큰만 후보로 남기고 그중에서 샘플링한다. 예: `k=50`이면 매 스텝 상위 50개만 고려. 작을수록 안전·단조, 클수록 다양. 분포 모양과 무관하게 개수로 자른다. |
| `top_p` (nucleus sampling) | 확률 높은 순으로 누적합이 `p`(예: 0.9)가 될 때까지의 토큰만 후보로 남긴다. 후보 **개수가 분포에 따라 가변** — 확신 있을 땐 적게, 애매할 땐 많이. 보통 `top_k`보다 선호된다. `temperature`와 함께 가장 흔히 조절. |
| `min_p` | 최고 확률 토큰 대비 일정 비율(예: 0.05) 미만인 토큰을 잘라낸다. `top_p`의 대안으로 분포가 평평할 때 품질이 낫다는 평. vLLM 등 오픈 엔진에서 지원. |
| greedy decoding | 매 스텝 가장 확률 높은 토큰 1개만 고름(= 샘플링 안 함, `temperature=0`/`top_k=1`에 해당). 가장 결정적이지만 단조롭고 반복에 빠질 수 있다. |

> 적용 순서(일반적): `temperature`로 분포를 데운 뒤 → `top_k`/`top_p`/`min_p`로 후보를 자르고 → 남은 분포에서 샘플링. OpenAI API는 `temperature`·`top_p`만 노출하고 `top_k`는 미지원(많은 오픈웨이트 엔진/vLLM은 셋 다 지원). 보통 **temperature와 top_p 중 하나만** 주로 만지길 권장.

## LLM Serving

| 용어 | 뜻 |
|---|---|
| Gateway | 클라이언트와 LLM provider 사이의 API 서버. 인증, 라우팅, 비용 계측, fallback, rate limit을 중앙화한다. |
| Adapter | 서로 다른 API shape를 내부 표준 모델로 바꾸는 계층. Chat `messages/choices`와 Responses `input/output` 차이를 흡수한다. |
| Routing | 요청 특성에 따라 모델, API, tier를 고르는 로직. 비용, latency, 품질을 제어하는 핵심 지점이다. |
| Fallback | 실패하거나 품질/latency 조건을 못 맞출 때 대체 경로로 보내는 전략. 장애와 quota 문제를 줄인다. |
| Rerank | 여러 후보나 검색 결과를 다시 점수화해 고르는 과정. `n`, retrieval, agent 후보 선택에서 사용한다. |
| Trace | 한 요청에서 일어난 model call, tool call, token, latency 기록. 디버깅, 비용 분석, audit에 필요하다. |
| Idempotency | 같은 요청을 여러 번 보내도 결과나 부작용이 중복되지 않는 성질. side-effect tool call에 필수다. |
| PII | 개인 식별 정보. metadata, prompt, logs에 원문 저장하지 않도록 정책이 필요하다. |
