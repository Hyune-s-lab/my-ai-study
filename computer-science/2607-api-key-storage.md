# API Key 안전한 저장과 검증

API Key는 비밀번호와 같다. 평문으로 저장하면 DB 유출 시 모든 키가 즉시 노출된다. 해시로 저장하더라도, 검증·조회·매핑 등 실무에서 마주하는 문제가 많다.

## 1. 왜 평문 저장이 위험한가

| 저장 방식 | DB 유출 시 | 알아야 할 것 |
|---|---|---|
| 평문 | 모든 키 즉시 노출. 공격자가 즉시 API 호출 가능 | 절대 금지 |
| 단순 해시(SHA-256) | 레인보우 테이블로 역추적 가능 | salt 없으면 위험 |
| bcrypt / Argon2 | 역추적 사실상 불가능 | 권장 |
| AES 암호화 | 복호키가 있으면 원문 복구 가능 | 키 관리 필요, 양방향 |

- **API Key는 비밀번호처럼 취급한다.** 저장은 해시, 검증은 비교. 원문은 발급 시점에만 보여주고 다시 볼 수 없게.
- JWT와 다르다. JWT는 서명 검증이고, API Key는 "이 키가 유효한가"를 DB 조회로 확인하는 것.

## 2. 해시 알고리즘 — bcrypt, salt, pepper

### bcrypt

비밀번호 해시의 사실상 표준. SHA-256과의 차이:

| | SHA-256 | bcrypt |
|---|---|---|
| 목적 | 빠른 무결성 검증 | 느린 비밀번호 해시 |
| 속도 | 매우 빠름 | 의도적으로 느림 (cost factor) |
| cost factor | — | 10(기본) → 약 100ms. 1 올라갈 때마다 2배 느려짐. 특별한 이유 없으면 기본값 사용 |
| salt | 직접 추가해야 함 | 내장 (해시 결과에 salt 포함) |
| 레인보우 테이블 | salt 없으면 취약 | cost 때문에 사실상 불가능 |
| 적합 | 파일 체크섬, 데이터 무결성 | 비밀번호, API Key |

```kotlin
// Spring Security BCryptPasswordEncoder
@Bean
fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()  // 기본 cost 10

// 저장
val keyHash = passwordEncoder.encode(rawApiKey)

// 검증
val matched = passwordEncoder.matches(rawApiKey, storedKeyHash)
```

### Argon2 (최신 대안)

bcrypt의 다음 세대. 2015년 암호 해싱 대회 우승작.

| | bcrypt (1999) | Argon2 (2015) |
|---|---|---|
| 방식 | CPU 연산 (Blowfish) | CPU + **메모리** 둘 다 사용 |
| 조절 가능 | cost (CPU 시간) | time, memory, parallelism |
| 검증 속도 (단일) | cost 10 기준 약 100ms | 설정에 따라 다르지만 유사하거나 약간 느림 |
| 메모리 사용량 | 약 4KB (거의 없음) | 64MB+ (설정 가능) |
| GPU/ASIC 저항 | 약함 (GPU로 빠르게 계산 가능) | 강함 (메모리를 많이 써서 GPU 병렬화 무력화) |
| 공격자 크랙 속도 | GPU 기준 초당 수백만 회 시도 가능 | GPU 기준 초당 수천 회 수준 (메모리 병목) |
| 취약점 | 72바이트 입력 제한 | 없음 |
| 채택 | 대부분의 서비스 (검증됨) | 최신 표준 (OWASP 1순위) |

```kotlin
// Spring Security 6+ — Argon2
@Bean
fun passwordEncoder(): PasswordEncoder = Argon2PasswordEncoder(
    16,      // salt 길이 (바이트)
    32,      // 해시 길이 (바이트)
    1,       // parallelism (스레드 수)
    65536,   // memory (KB) = 64MB
    3        // iterations
)
```

### salt

- 각 키마다 **고유한 랜덤 값**을 해시 입력에 추가.
- 같은 키를 두 번 발급해도 해시값이 다름 → 레인보우 테이블 무력화.
- bcrypt는 salt를 내장: 해시 결과 자체에 salt가 포함되어 있어서 따로 컬럼을 만들 필요 없음.

bcrypt 해시 구조:
```
$2a$12$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy
     │  │ └──── salt (22자) ────┘ └──── 해시 결과 ────┘
     │  └─ cost factor (12)
     └─ bcrypt 버전 (2a)
```

### pepper

- salt와 달리 **서버만 아는 고정 비밀값**. 해시 입력에 추가.
- salt는 DB에 저장되지만, pepper는 DB가 유출돼도 알 수 없음 (코드/환경변수에 있으므로).
- DB 유출 + 코드 유출이 같이 일어나지 않는 한, pepper가 있는 해시는 깰 수 없음.

```kotlin
// pepper 적용 — 환경변수에서 pepper 읽기
@Value("\${api.key.pepper}")
private lateinit var pepper: String

fun hashApiKey(rawApiKey: String): String {
    val pepperedKey = rawApiKey + pepper
    return passwordEncoder.encode(pepperedKey)
}
```

| | salt | pepper |
|---|---|---|
| 키마다 다른가 | ✅ (각 키마다 고유) | ❌ (서버 고정값) |
| 저장 위치 | DB (해시 결과에 내장) | 환경변수 / Vault / KMS |
| DB 유출 시 | 유출됨 (하지만 의미 없음) | 유출 안 됨 |
| 역할 | 레인보우 테이블 방지 | DB 유출 시 추가 방어선 |

## 3. 클라이언트 요청값과 영속화된 값의 매핑

API Key는 `sk-{key_id}-{secret}` 같은 형태. 클라이언트는 이 원문을 보내고, DB에는 해시가 저장되어 있다.

### 검증 흐름

```
1. 클라이언트가 Authorization: Bearer sk-{key_id}-{secret} 로 요청
2. 게이트웨이가 raw key 추출
3. key_id로 DB 조회 (인덱스, 빠름)
4. bcrypt.matches(secret, storedHash)로 비교 (느리지만 한 번만)
5. 일치하면 인증 완료, 키 메타데이터(tenant_id, 권한 등) 반환
```

### 핵심 문제: 해시된 값으로는 직접 조회할 수 없다

bcrypt는 단방향 해시다. 클라이언트가 보낸 `sk-xxxx`를 해시해서 `WHERE key_hash = ?`로 조회하면 안 되냐? **안 된다.** bcrypt는 salt가 내장되어 있어서 같은 입력을 넣어도 매번 다른 해시가 나온다.

**해결: 키 식별자(key_id)를 분리**

API Key를 두 부분으로 구성:

```
sk-{key_id}-{secret}

예: sk-a1b2c3d4-xY9kF2mNpQ7vR3wL8tZ5
     └── key_id ──┘ └──── secret ────┘
```

| 부분 | 역할 | 저장 |
|---|---|---|
| `key_id` | DB 조회용 인덱스 | 평문 저장 (인덱싱 가능) |
| `secret` | 인증용 비밀값 | bcrypt 해시 저장 |

```sql
CREATE TABLE api_key (
    id          BIGSERIAL PRIMARY KEY,
    key_id      VARCHAR(32) UNIQUE NOT NULL,  -- 평문, 조회용
    key_hash    TEXT NOT NULL,                 -- bcrypt(secret), 검증용
    tenant_id   BIGINT NOT NULL,
    allowed_models TEXT[],
    status      VARCHAR(10) NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE, REVOKED
    expires_at  TIMESTAMP,
    created_at   TIMESTAMP DEFAULT now()
);

CREATE INDEX idx_api_key_key_id ON api_key(key_id);
```

### 검증 로직

```kotlin
@Service
class ApiKeyService(
    private val apiKeyRepository: ApiKeyRepository,
    private val passwordEncoder: PasswordEncoder
) {
    fun validate(rawApiKey: String): ApiKey? {
        // 1. 파싱: sk-{key_id}-{secret}
        val parts = rawApiKey.split("-", limit = 3)
        if (parts.size < 3) return null

        val keyId = parts[1]
        val secret = parts[2]

        // 2. key_id로 DB 조회 (인덱스 탐색, 빠름)
        val apiKey = apiKeyRepository.findByKeyIdAndStatus(keyId, "ACTIVE")
            ?: return null

        // 3. 만료 확인
        if (apiKey.expiresAt?.isBefore(LocalDateTime.now()) == true) {
            return null
        }

        // 4. secret 해시 비교 (bcrypt, 느리지만 한 번만)
        if (!passwordEncoder.matches(secret, apiKey.keyHash)) {
            return null
        }

        return apiKey
    }
}
```

### 왜 이 구조가 맞는가

| 방식 | 조회 | 검증 | 문제 |
|---|---|---|---|
| 전체 키를 해시해서 조회 | 전체 스캔 (bcrypt는 매번 다른 해시) | — | 느림, 사실상 불가능 |
| 전체 키를 평문 저장 | 빠름 | 단순 비교 | DB 유출 시 즉시 노출 |
| key_id(평문) + secret(해시) | key_id로 인덱스 조회 | bcrypt로 secret 비교 | 빠른 조회 + 안전한 검증 ✅ |

## 4. API Key 기반으로 다른 데이터를 조회할 때

인증이 끝나면, 이 키로 어떤 tenant인지, 어떤 모델을 쓸 수 있는지, 호출 이력은 어떻게 되는지 조회해야 한다.

### key_id를 외래키로 사용

```sql
-- 호출 이력
CREATE TABLE request_record (
    id          BIGSERIAL PRIMARY KEY,
    api_key_id  BIGINT NOT NULL REFERENCES api_key(id),
    tenant_id   BIGINT NOT NULL,
    model_id    VARCHAR(100) NOT NULL,
    status      VARCHAR(20) NOT NULL,
    latency_ms  INT,
    requested_at TIMESTAMP DEFAULT now()
);

CREATE INDEX idx_request_record_api_key_id ON request_record(api_key_id);
CREATE INDEX idx_request_record_tenant_id ON request_record(tenant_id, requested_at DESC);
```

- 인증이 끝나면 `api_key.id` (PK)를 얻는다. 이후 모든 조회는 이 id로.
- `key_id`는 인증에만 쓰고, 이후에는 PK로 조회하는 게 성능상 유리.
- `tenant_id`는 `api_key` 테이블에서 가져와서 `request_record`에도 중복 저장 (조회 시 join 회피).

### 캐싱 — 매번 bcrypt 검증은 비싸다

bcrypt는 의도적으로 느리다 (기본 cost 10이면 약 100ms). 매 요청마다 검증하면 병목.

```kotlin
@Service
class ApiKeyCacheService(
    private val apiKeyService: ApiKeyService,
    private val passwordEncoder: PasswordEncoder,
    private val redisTemplate: StringRedisTemplate
) {
    fun validate(rawApiKey: String): ApiKey? {
        val parts = rawApiKey.split("-", limit = 3)
        if (parts.size < 3) return null
        val keyId = parts[1]
        val secret = parts[2]

        val cacheKey = "apikey:$keyId"

        // 1. 캐시 조회
        val cached = redisTemplate.opsForValue().get(cacheKey)
        if (cached != null) {
            // 캐시 히트 — bcrypt 검증은 여전히 필요 (secret 비교)
            val apiKey = deserialize(cached)
            if (passwordEncoder.matches(secret, apiKey.keyHash)) {
                return apiKey
            }
            return null
        }

        // 2. 캐시 미스 — DB 조회 + bcrypt 검증
        val apiKey = apiKeyService.validate(rawApiKey) ?: return null

        // 3. 캐싱 (TTL 5분)
        redisTemplate.opsForValue().set(cacheKey, serialize(apiKey), Duration.ofMinutes(5))

        return apiKey
    }

    fun revoke(keyId: String) {
        // 폐기 시 캐시 즉시 삭제
        redisTemplate.delete("apikey:$keyId")
    }
}
```

- 캐시 TTL은 짧게 (5분). 키가 폐기되면 캐시도 빨리 만료되도록.
- 폐기 시 캐시 즉시 삭제: `revoke(keyId)`.
- **주의**: 캐시에 key_hash를 같이 넣으면 Redis 유출 시 해시가 노출. hash는 단방향이라 괜찮지만, pepper가 없다면 레인보우 테이블 위험. pepper를 쓰면 안전.

## 5. 실무 체크리스트

| 항목 | 권장 |
|---|---|
| 저장 | bcrypt(기본 cost) 또는 Argon2 |
| salt | bcrypt 내장 (별도 컬럼 불필요) |
| pepper | 환경변수 / Vault에 저장, 해시 입력에 추가 |
| 키 구조 | `sk-{key_id}-{secret}` 분리 |
| 조회 | key_id(평문)로 인덱스 조회 → secret(해시) 비교 |
| 캐싱 | Redis에 검증 결과 캐싱 (TTL 짧게), 폐기 시 즉시 삭제 |
| 폐기 | status = REVOKED + 캐시 삭제 |
| 발급 시점 | 원문은 한 번만 보여주고, 이후는 복구 불가 |
| 로그 | API Key 원문을 로그에 남기지 않는다 (key_id만 남김) |
