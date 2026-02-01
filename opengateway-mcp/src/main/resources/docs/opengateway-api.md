# OpenGateway API 문서

OpenGateway는 여러 AI 모델 제공업체를 통합하여 단일 API로 접근할 수 있는 게이트웨이 서비스입니다.

## 인증

### API 키 발급

OpenGateway 콘솔에서 API 키를 발급받을 수 있습니다.

1. 콘솔(https://console.opengateway.io)에 로그인
2. Settings → API Keys 메뉴 이동
3. "Create New Key" 버튼 클릭
4. 키 이름 입력 후 생성

### 인증 헤더

모든 API 요청에는 다음 헤더가 필요합니다:

```
Authorization: Bearer {YOUR_API_KEY}
x-opengateway-user-id: {OPTIONAL_USER_ID}
```

| 헤더 | 필수 | 설명 |
|------|------|------|
| Authorization | O | Bearer 토큰 형식의 API 키 |
| x-opengateway-user-id | X | 사용량 추적을 위한 사용자 식별자 |

### 인증 오류

| 코드 | 메시지 | 원인 |
|------|--------|------|
| 401 | Invalid API key | API 키가 유효하지 않음 |
| 401 | API key expired | API 키가 만료됨 |
| 403 | Rate limit exceeded | 요청 한도 초과 |

## Chat Completions API

### 엔드포인트

```
POST https://api.opengateway.io/v1/chat/completions
```

### 요청 형식

```json
{
  "model": "gpt-4o",
  "messages": [
    {"role": "system", "content": "You are a helpful assistant."},
    {"role": "user", "content": "Hello!"}
  ],
  "temperature": 0.7,
  "max_tokens": 1000,
  "stream": false
}
```

### 요청 파라미터

| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| model | string | O | 사용할 모델 이름 |
| messages | array | O | 대화 메시지 배열 |
| temperature | number | X | 생성 다양성 (0-2, 기본값: 1) |
| max_tokens | integer | X | 최대 생성 토큰 수 |
| stream | boolean | X | 스트리밍 응답 여부 (기본값: false) |
| top_p | number | X | nucleus sampling 파라미터 |
| presence_penalty | number | X | 반복 패널티 (-2 ~ 2) |
| frequency_penalty | number | X | 빈도 패널티 (-2 ~ 2) |

### 메시지 역할

| 역할 | 설명 |
|------|------|
| system | 시스템 프롬프트, 모델의 행동 지침 |
| user | 사용자 입력 메시지 |
| assistant | 모델의 응답 메시지 |

### 응답 형식

```json
{
  "id": "chatcmpl-abc123",
  "object": "chat.completion",
  "created": 1705891234,
  "model": "gpt-4o",
  "choices": [
    {
      "index": 0,
      "message": {
        "role": "assistant",
        "content": "Hello! How can I help you today?"
      },
      "finish_reason": "stop"
    }
  ],
  "usage": {
    "prompt_tokens": 20,
    "completion_tokens": 10,
    "total_tokens": 30
  }
}
```

### 스트리밍 응답

`stream: true` 설정 시 Server-Sent Events(SSE) 형식으로 응답:

```
data: {"id":"chatcmpl-abc","choices":[{"delta":{"content":"Hello"}}]}

data: {"id":"chatcmpl-abc","choices":[{"delta":{"content":"!"}}]}

data: [DONE]
```

## 지원 모델

### OpenAI 모델

| 모델 | 입력 토큰 비용 | 출력 토큰 비용 | Context Window |
|------|--------------|--------------|----------------|
| gpt-4o | $2.5/1M | $10/1M | 128K |
| gpt-4o-mini | $0.15/1M | $0.6/1M | 128K |
| gpt-4-turbo | $10/1M | $30/1M | 128K |
| gpt-3.5-turbo | $0.5/1M | $1.5/1M | 16K |

### Anthropic 모델

| 모델 | 입력 토큰 비용 | 출력 토큰 비용 | Context Window |
|------|--------------|--------------|----------------|
| claude-3-5-sonnet-latest | $3/1M | $15/1M | 200K |
| claude-3-5-haiku-latest | $0.8/1M | $4/1M | 200K |
| claude-3-opus-latest | $15/1M | $75/1M | 200K |

### Google 모델

| 모델 | 입력 토큰 비용 | 출력 토큰 비용 | Context Window |
|------|--------------|--------------|----------------|
| gemini-2.0-flash | $0.1/1M | $0.4/1M | 1M |
| gemini-1.5-pro | $1.25/1M | $5/1M | 2M |

## Embeddings API

### 엔드포인트

```
POST https://api.opengateway.io/v1/embeddings
```

### 요청 형식

```json
{
  "model": "text-embedding-3-small",
  "input": "The quick brown fox jumps over the lazy dog.",
  "encoding_format": "float"
}
```

### 요청 파라미터

| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| model | string | O | 임베딩 모델 이름 |
| input | string/array | O | 임베딩할 텍스트 (문자열 또는 배열) |
| encoding_format | string | X | 인코딩 형식 (float 또는 base64) |
| dimensions | integer | X | 출력 벡터 차원 (일부 모델만 지원) |

### 응답 형식

```json
{
  "object": "list",
  "data": [
    {
      "object": "embedding",
      "index": 0,
      "embedding": [0.0023064255, -0.009327292, ...]
    }
  ],
  "model": "text-embedding-3-small",
  "usage": {
    "prompt_tokens": 8,
    "total_tokens": 8
  }
}
```

### 지원 임베딩 모델

| 모델 | 차원 | 비용 |
|------|------|------|
| text-embedding-3-small | 1536 | $0.02/1M tokens |
| text-embedding-3-large | 3072 | $0.13/1M tokens |

## 오류 처리

### 오류 응답 형식

```json
{
  "error": {
    "message": "Invalid API key provided",
    "type": "authentication_error",
    "code": "invalid_api_key"
  }
}
```

### 일반 오류 코드

| HTTP 코드 | type | 설명 |
|-----------|------|------|
| 400 | invalid_request_error | 잘못된 요청 파라미터 |
| 401 | authentication_error | 인증 실패 |
| 403 | permission_error | 권한 없음 |
| 404 | not_found | 리소스를 찾을 수 없음 |
| 429 | rate_limit_error | 요청 한도 초과 |
| 500 | server_error | 서버 내부 오류 |
| 503 | service_unavailable | 서비스 일시 불가 |

### 재시도 전략

429 또는 5xx 오류 발생 시 지수 백오프(exponential backoff)로 재시도 권장:

```kotlin
val delays = listOf(1000L, 2000L, 4000L, 8000L) // ms
for (delay in delays) {
    try {
        return makeRequest()
    } catch (e: RateLimitException) {
        Thread.sleep(delay)
    }
}
```

## SDK 사용 예시

### Kotlin/Spring 예시

```kotlin
@Service
class OpenGatewayService(
    @Value("\${opengateway.api-key}") private val apiKey: String
) {
    private val client = WebClient.builder()
        .baseUrl("https://api.opengateway.io/v1")
        .defaultHeader("Authorization", "Bearer $apiKey")
        .build()
    
    fun chat(messages: List<Message>): ChatResponse {
        return client.post()
            .uri("/chat/completions")
            .bodyValue(ChatRequest(
                model = "gpt-4o",
                messages = messages
            ))
            .retrieve()
            .bodyToMono(ChatResponse::class.java)
            .block()!!
    }
}
```

### cURL 예시

```bash
curl https://api.opengateway.io/v1/chat/completions \
  -H "Authorization: Bearer $OPENGATEWAY_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "gpt-4o",
    "messages": [{"role": "user", "content": "Hello!"}]
  }'
```

## Rate Limits

### 기본 제한

| 플랜 | RPM (분당 요청) | TPM (분당 토큰) |
|------|----------------|----------------|
| Free | 10 | 10,000 |
| Pro | 100 | 100,000 |
| Enterprise | 1000 | 1,000,000 |

### 헤더로 확인

응답 헤더에서 현재 사용량 확인 가능:

```
x-ratelimit-limit-requests: 100
x-ratelimit-remaining-requests: 95
x-ratelimit-reset-requests: 60s
```
