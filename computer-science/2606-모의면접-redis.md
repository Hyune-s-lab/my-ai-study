# 모의면접 — Redis

> 면접관이 Redis로 보려는 것은 **명령어 암기가 아니라**: 왜 빠른지(아키텍처), 캐시가 무너지는 3대 패턴(stampede·penetration·avalanche)을 아는지, **데이터 유실·일관성의 한계**를 알고 쓰는지다.
> 형식: **Q(질문) → 모범답안 → 꼬리질문 → 감점 포인트**.

## 레벨 1 — 개념

### Q1. Redis는 왜 빠른가요?

**모범답안**: 세 가지가 핵심이다.

- **인메모리**: 모든 데이터를 RAM에서 읽고 쓴다. 디스크 I/O가 경로에 없다.
- **싱글 스레드 이벤트 루프**: 명령 실행이 단일 스레드라 **락·컨텍스트 스위칭 비용이 없다.** I/O multiplexing(epoll)으로 수만 커넥션을 한 스레드가 처리.
- **효율적 자료구조**: SDS, skiplist, listpack 등 메모리·연산에 최적화된 내부 구현.

> 단서 달기: "싱글 스레드"는 **명령 실행** 얘기다. Redis 6+는 **I/O(읽기/쓰기 파싱)는 멀티스레드** 옵션이 있고, 백그라운드 작업(RDB fork, AOF rewrite, lazy free)도 별도 스레드/프로세스다.

**꼬리질문**: 싱글 스레드인데 어떻게 수만 동시 클라이언트를 받나요?
→ I/O multiplexing. 이벤트 루프가 준비된 소켓만 골라 처리한다(nginx·Node.js와 같은 모델). 각 명령이 마이크로초 단위로 짧아서 한 스레드로도 초당 수십만 op 처리.

**감점 포인트**: "메모리라서 빠르다" 한 줄로 끝. 싱글 스레드 모델과 그 **양날의 검**(다음 질문)을 못 엮으면 얕다고 본다.

### Q2. 싱글 스레드라서 생기는 위험은 뭔가요?

**모범답안**: **느린 명령 하나가 전체를 막는다.** 이벤트 루프라 한 명령이 오래 걸리면 그 뒤 모든 클라이언트가 줄줄이 대기한다.

- **`KEYS *`**: 전체 키 스캔 O(N) → 프로덕션 금지. 대신 **`SCAN`**(커서 기반, 점진적).
- **big key**: 수백 MB 해시/리스트를 `DEL`·`HGETALL` → 루프 블로킹. 삭제는 **`UNLINK`**(lazy free), 조회는 분할.
- **O(N) 명령들**: `SMEMBERS`, `LRANGE 0 -1`, `SORT` 등 — 데이터가 커지면 시한폭탄.
- 진단: `SLOWLOG`, `latency monitor`.

**꼬리질문**: big key는 어떻게 찾고 어떻게 해소하나요?
→ 찾기: `redis-cli --bigkeys`, `MEMORY USAGE key`. 해소: 키를 샤딩(해시 필드 분할, `user:1:orders:0~9`처럼 버킷팅)하거나 자료구조 재설계. 삭제는 `UNLINK`로.

**감점 포인트**: `KEYS`를 아무렇지 않게 쓰는 사람. "운영 중 Redis가 순간 멈췄다" 류 장애의 단골 원인을 모르는 것.

### Q3. 자료구조별로 언제 뭘 쓰나요?

**모범답안**: String만 쓰면 Redis의 절반만 쓰는 것이다.

| 자료구조 | 대표 용도 |
|---|---|
| **String** | 단순 캐시, 카운터(`INCR`), 분산락(`SET NX`) |
| **Hash** | 객체 필드 단위 저장/수정 (유저 프로필 — 필드만 갱신) |
| **List** | 간단한 큐/스택, 최근 N개 목록(`LPUSH`+`LTRIM`) |
| **Set** | 중복 제거, 교집합(공통 친구), 태그 |
| **Sorted Set (ZSET)** | **랭킹/리더보드**, 우선순위 큐, sliding window rate limit |
| **Streams** | 컨슈머 그룹 있는 메시지 큐(Kafka-lite) |
| **HyperLogLog** | 대용량 유니크 카운트 근사 (UV) — 12KB 고정 |
| **Bitmap** | 출석체크·플래그 (일별 1비트) |

**꼬리질문**: 리더보드를 RDB로 하면 왜 힘든가요?
→ 순위 조회가 매번 `ORDER BY` + 정렬 비용. ZSET은 skiplist라 삽입·순위 조회 모두 O(log N), `ZRANK`/`ZRANGE`로 즉시 순위·구간 조회.

**감점 포인트**: String/get/set만 답. 자료구조 선택이 곧 Redis 설계 능력이다.

## 레벨 2 — 캐시 설계 (실무 단골)

### Q4. 캐싱 전략(패턴)을 설명해보세요.

**모범답안**:

| 패턴 | 동작 | 특징 |
|---|---|---|
| **Cache-Aside** (Look-Aside) | 앱이 캐시 미스 시 DB 조회 → 캐시에 적재 | 가장 흔함. 캐시 장애에도 DB로 동작(성능만 저하) |
| **Read-Through** | 캐시 계층이 직접 DB 조회 | 앱 코드 단순, 캐시 라이브러리/프록시 필요 |
| **Write-Through** | 쓰기를 캐시+DB 동시에 | 읽기 일관성↑, 쓰기 지연↑ |
| **Write-Behind** (Write-Back) | 캐시에 쓰고 DB는 비동기 배치 | 쓰기 빠름, **유실 위험** |

쓰기 시 캐시 처리는 보통 **"DB 갱신 후 캐시 삭제(invalidate)"** — 갱신(update)보다 삭제가 안전하다(동시 쓰기 시 stale 값이 남는 race가 줄어듦).

**꼬리질문**: "캐시 갱신"이 아니라 "삭제"를 권하는 이유는?
→ 두 요청이 동시에 DB를 다르게 갱신하면, 캐시 set 순서가 DB 커밋 순서와 어긋나 **오래된 값이 캐시에 남을 수 있다.** 삭제하면 다음 읽기가 DB에서 최신을 다시 적재. (그래도 완전하진 않아서 TTL을 안전망으로 깐다.)

**감점 포인트**: cache-aside 하나만 알고, 쓰기 경로(invalidate vs update)의 race를 생각 안 해본 답.

### Q5. 캐시 장애 3대 패턴 — stampede, penetration, avalanche를 설명해보세요.

**모범답안**: 이름은 달라도 전부 "**미스가 폭주해 DB를 때리는**" 패턴이다.

| 문제 | 무엇 | 대응 |
|---|---|---|
| **Cache Stampede** (thundering herd) | **인기 키 하나**가 만료되는 순간, 수천 요청이 동시에 DB로 | ① **분산락/뮤텍스** — 한 요청만 재적재, 나머지는 대기·stale 반환 ② **logical expiration** — 물리 TTL 없이 값 안에 만료시각, 백그라운드 갱신 ③ early refresh |
| **Cache Penetration** (관통) | **존재하지 않는 키** 조회 — 캐시에도 DB에도 없어 매번 DB 직행 (악의적 가능) | ① **null 캐싱**(짧은 TTL) ② **Bloom filter**로 존재 여부 선차단 |
| **Cache Avalanche** (눈사태) | **수많은 키가 동시에** 만료(같은 TTL) 또는 Redis 자체 다운 | ① **TTL에 jitter**(무작위 가산) ② 다층 캐시(로컬+Redis) ③ Redis HA + 서킷브레이커/fallback |

**꼬리질문**: hot key(특정 키에 트래픽 집중)는 어떻게 다루나요?
→ ① **로컬 캐시**(Caffeine) 한 겹 — 짧은 TTL로 Redis 부하 흡수 ② 키 복제(`key:1~N`으로 분산 후 랜덤 조회) ③ 클러스터에선 hot key가 한 샤드에 몰리므로 특히 중요.

**감점 포인트**: 세 용어를 구분 못 하거나 대응책 없이 현상만 설명. **TTL jitter** 같은 구체 수단이 안 나오면 운영 경험 의심.

> **캐시 3대 장애 패턴 흐름** — 미스가 폭주해 DB를 때리는 세 가지 양상과 대응.

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
    edgeLabelBackground: "#ffffff"
---
flowchart LR
  subgraph canvas[" "]
    direction TB

    subgraph stampede["Cache Stampede — 인기 키 만료 폭주"]
      direction TB
      ST1["수천 요청 동시 도달"]
      ST2@{ img: "https://icons.terrastruct.com/dev/redis.svg", label: "Redis MISS", pos: "b", h: 48, constraint: "on" }
      ST3@{ img: "https://icons.terrastruct.com/dev/postgresql.svg", label: "DB 동시 조회", pos: "b", h: 48, constraint: "on" }
      ST1 --> ST2 --> ST3
    end

    subgraph penetration["Cache Penetration — 없는 키 관통"]
      direction TB
      PT1["악의적 요청 (존재 않는 키)"]
      PT2@{ img: "https://icons.terrastruct.com/dev/redis.svg", label: "Redis MISS", pos: "b", h: 48, constraint: "on" }
      PT3@{ img: "https://icons.terrastruct.com/dev/postgresql.svg", label: "DB 직행 MISS", pos: "b", h: 48, constraint: "on" }
      PT4["매번 DB 직행 반복"]
      PT1 --> PT2 --> PT3 --> PT4
    end

    subgraph avalanche["Cache Avalanche — 일괄 만료"]
      direction TB
      AV1["다수 키 동시 만료 (같은 TTL)"]
      AV2@{ img: "https://icons.terrastruct.com/dev/redis.svg", label: "대량 MISS 발생", pos: "b", h: 48, constraint: "on" }
      AV3@{ img: "https://icons.terrastruct.com/dev/postgresql.svg", label: "DB 부하 폭증", pos: "b", h: 48, constraint: "on" }
      AV1 --> AV2 --> AV3
    end

    subgraph fix["대응"]
      direction TB
      FX1["Stampede: 분산락·logical expire"]
      FX2["Penetration: null 캐싱·Bloom filter"]
      FX3["Avalanche: TTL jitter·다층 캐시"]
    end
  end

  classDef app fill:#EFF6FF,stroke:#3B5BA5,stroke-width:1px,color:#16213E
  classDef db fill:#F0FDF4,stroke:#3F8E55,stroke-width:1px,color:#14532D
  classDef ctrl fill:#FFF7ED,stroke:#C98A2B,stroke-width:1px,color:#7A4E0A
  classDef icon fill:transparent,stroke:transparent,stroke-width:0px,color:#111827

  class ST1,PT1,PT4,AV1,FX1,FX2,FX3 app
  class ST2,PT2,AV2,ST3,PT3,AV3 icon
  class FX1,FX2,FX3 ctrl

  style stampede fill:#F8FAFC,stroke:#CBD5E1,stroke-width:1px,color:#111827
  style penetration fill:#F8FAFC,stroke:#CBD5E1,stroke-width:1px,color:#111827
  style avalanche fill:#F8FAFC,stroke:#CBD5E1,stroke-width:1px,color:#111827
  style fix fill:#F8FAFC,stroke:#CBD5E1,stroke-width:1px,color:#111827
  style canvas fill:#ffffff,stroke:#ffffff,stroke-width:0px,color:#111827
```

### Q6. TTL 만료와 메모리 축출(eviction)은 어떻게 동작하나요?

**모범답안**: 둘은 다른 메커니즘이다.

- **만료(expiration)**: TTL 지난 키 제거. **lazy**(접근 시 확인 후 삭제) + **active**(주기적으로 만료 키 샘플링 삭제) 조합. → 만료 시각이 지나도 **즉시 사라지는 게 아니다.**
- **축출(eviction)**: `maxmemory` 도달 시 정책에 따라 키를 쫓아냄.

| 정책 | 동작 |
|---|---|
| `noeviction` (기본) | 쫓아내지 않고 **쓰기 에러** — 캐시 용도면 부적합한 기본값! |
| `allkeys-lru` | 전체 키 중 LRU 제거 — **순수 캐시면 보통 이것** |
| `volatile-lru` | TTL 있는 키 중 LRU |
| `allkeys-lfu` / `volatile-lfu` | 빈도 기반(LFU) — hot/cold 구분이 뚜렷하면 |
| `volatile-ttl` | TTL 임박한 키부터 |

> Redis의 LRU/LFU는 **근사(샘플링)** 알고리즘이다 — 정확한 LRU가 아니다(메모리 절약 목적).

**꼬리질문**: 캐시 전용 Redis인데 `noeviction` 기본값을 그대로 두면?
→ 메모리 차면 쓰기가 전부 에러 → 캐시 적재 실패가 앱 에러로 번진다. 캐시 용도면 `allkeys-lru`(또는 lfu)로 바꾸는 게 첫 체크리스트.

**감점 포인트**: 만료=즉시 삭제로 아는 것, 기본 정책이 `noeviction`인 걸 모르는 것.

## 레벨 3 — 영속성·HA·분산 (여기서 갈림)

### Q7. RDB와 AOF의 차이와 선택 기준은?

**모범답안**:

| | **RDB** (snapshot) | **AOF** (append-only log) |
|---|---|---|
| 방식 | 특정 시점 메모리 덤프(`fork` + COW) | 쓰기 명령을 로그로 기록 |
| 유실 | 마지막 스냅샷 이후 **분 단위 유실** 가능 | `appendfsync everysec`(기본) 기준 **최대 1초** |
| 복구 속도 | 빠름(바이너리 로드) | 느림(명령 재실행) — rewrite로 압축 |
| 비용 | fork 순간 메모리 스파이크(COW) | 디스크 쓰기 지속 부하 |

실무 선택:

- **순수 캐시**(원본이 DB에 있음) → 영속성 끄거나 RDB만 가볍게.
- **유실이 아프면**(세션·카운터 등) → AOF everysec, 보통 **RDB+AOF 혼용**(Redis 7 기본 AOF는 RDB preamble 하이브리드).
- 어떤 조합도 **"절대 유실 없음"은 아니다** — Redis를 source of truth로 쓰지 않는 게 원칙.

**꼬리질문**: RDB fork 시 메모리가 2배 필요하다는 말이 왜 나오나?
→ fork 후 copy-on-write라 평소엔 공유하지만, **쓰기가 많으면 변경 페이지가 복제**돼 최악엔 원본만큼 추가 메모리. 쓰기 폭주 시간대에 BGSAVE 잡으면 OOM 위험.

**감점 포인트**: "AOF가 안전하니 AOF" 식 단답. fork 비용·everysec 1초 유실·하이브리드를 모르면 운영 안 해본 것.

> **RDB vs AOF 영속성 흐름** — 스냅샷 방식과 로그 방식의 차이.

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
    edgeLabelBackground: "#ffffff"
---
flowchart LR
  subgraph canvas[" "]
    direction TB

    subgraph rdb["RDB (Snapshot)"]
      direction TB
      RD1@{ img: "https://icons.terrastruct.com/dev/redis.svg", label: "Redis 메모리", pos: "b", h: 48, constraint: "on" }
      RD2["fork + COW"]
      RD3["덤프 파일 (바이너리)"]
      RD1 --> RD2 --> RD3
      RD4["복구: 빠름 (파일 로드)"]
      RD5["유실: 마지막 스냅샷 이후 분 단위"]
      RD3 --> RD4
      RD4 --> RD5
    end

    subgraph aof["AOF (Append-Only Log)"]
      direction TB
      AO1@{ img: "https://icons.terrastruct.com/dev/redis.svg", label: "쓰기 명령", pos: "b", h: 48, constraint: "on" }
      AO2["append-only 로그 기록"]
      AO3["everysec 동기화 (기본)"]
      AO4["복구: 느림 (명령 재실행)"]
      AO5["유실: 최대 1초"]
      AO1 --> AO2 --> AO3
      AO3 --> AO4
      AO4 --> AO5
    end
  end

  classDef app fill:#EFF6FF,stroke:#3B5BA5,stroke-width:1px,color:#16213E
  classDef db fill:#F0FDF4,stroke:#3F8E55,stroke-width:1px,color:#14532D
  classDef ctrl fill:#FFF7ED,stroke:#C98A2B,stroke-width:1px,color:#7A4E0A
  classDef icon fill:transparent,stroke:transparent,stroke-width:0px,color:#111827

  class RD1,AO1 icon
  class RD2,RD3,RD4,AO2,AO3,AO4 db
  class RD5,AO5 ctrl

  style rdb fill:#F8FAFC,stroke:#CBD5E1,stroke-width:1px,color:#111827
  style aof fill:#F8FAFC,stroke:#CBD5E1,stroke-width:1px,color:#111827
  style canvas fill:#ffffff,stroke:#ffffff,stroke-width:0px,color:#111827
```

### Q8. Sentinel과 Cluster의 차이는? 언제 뭘 쓰나요?

**모범답안**: 푸는 문제가 다르다.

| | **Sentinel** | **Cluster** |
|---|---|---|
| 해결 문제 | **HA** (장애 감지 + 자동 failover) | **샤딩**(수평 확장) + HA |
| 데이터 분산 | 없음 — 마스터 1대에 전체 데이터 | **16384 hash slot**으로 분산 |
| 적용 기준 | 데이터가 한 노드 메모리에 들어갈 때 | 메모리/쓰기 처리량이 한 노드를 넘을 때 |
| 제약 | — | **멀티 키 연산은 같은 슬롯**이어야(MGET, 트랜잭션 등) → **hash tag** `{user:1}:a` 로 슬롯 고정 |

**꼬리질문**: failover 때 데이터가 유실될 수 있나요?
→ 있다. **복제가 비동기**라 마스터가 죽는 순간 replica에 아직 안 간 쓰기는 사라진다. 승격된 replica 기준으로 진행되고, 구 마스터가 복구돼도 그 쓰기는 버려진다. → "Redis에 쓴 것 = 영구 보장"이 아니라는 인식이 중요.

**감점 포인트**: Sentinel과 Cluster를 "둘 다 HA 솔루션" 정도로 뭉뚱그림. 비동기 복제 유실을 모르면 분산 이해 부족.

> **Sentinel vs Cluster 구조** — HA 장애 감지 vs 샤딩 수평 확장.

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
    edgeLabelBackground: "#ffffff"
---
flowchart LR
  subgraph canvas[" "]
    direction TB

    subgraph sentinel["Sentinel — HA (장애 감지 + Failover)"]
      direction TB
      SE1["Sentinel x3 (감지 투표)"]
      SE2@{ img: "https://icons.terrastruct.com/dev/redis.svg", label: "Master 1대", pos: "b", h: 48, constraint: "on" }
      SE3@{ img: "https://icons.terrastruct.com/dev/redis.svg", label: "Replica 1대", pos: "b", h: 48, constraint: "on" }
      SE1 --> SE2
      SE1 --> SE3
      SE2 --> SE3
      SE4["전체 데이터 1노드"]
      SE2 --> SE4
    end

    subgraph cluster["Cluster — 샤딩 (수평 확장) + HA"]
      direction TB
      CL1@{ img: "https://icons.terrastruct.com/dev/redis.svg", label: "Slot 0~5460\nMaster A", pos: "b", h: 48, constraint: "on" }
      CL2@{ img: "https://icons.terrastruct.com/dev/redis.svg", label: "Slot 5461~10922\nMaster B", pos: "b", h: 48, constraint: "on" }
      CL3@{ img: "https://icons.terrastruct.com/dev/redis.svg", label: "Slot 10923~16383\nMaster C", pos: "b", h: 48, constraint: "on" }
      CL4["16384 hash slot 분산"]
      CL1 --> CL4
      CL2 --> CL4
      CL3 --> CL4
    end
  end

  classDef app fill:#EFF6FF,stroke:#3B5BA5,stroke-width:1px,color:#16213E
  classDef db fill:#F0FDF4,stroke:#3F8E55,stroke-width:1px,color:#14532D
  classDef ctrl fill:#FFF7ED,stroke:#C98A2B,stroke-width:1px,color:#7A4E0A
  classDef icon fill:transparent,stroke:transparent,stroke-width:0px,color:#111827

  class SE2,SE3,CL1,CL2,CL3 icon
  class SE4,CL4 ctrl

  style sentinel fill:#F8FAFC,stroke:#CBD5E1,stroke-width:1px,color:#111827
  style cluster fill:#F8FAFC,stroke:#CBD5E1,stroke-width:1px,color:#111827
  style canvas fill:#ffffff,stroke:#ffffff,stroke-width:0px,color:#111827
```

### Q9. Redis 분산락을 구현해보세요. 주의점은?

**모범답안**: 기본형은 한 줄이다.

```
SET lock:order:123 {uuid} NX PX 5000
```

- **NX**: 없을 때만 (원자적 획득) / **PX**: TTL — 락 보유자가 죽어도 자동 해제(데드락 방지)
- **value에 uuid**: 해제 시 **내 락인지 확인 후 삭제**해야 한다. 안 그러면 TTL 만료 후 남의 락을 지우는 사고. 이 "확인+삭제"는 원자적이어야 해서 직접 구현하면 까다롭다 → **실무는 Redisson `RLock`** — uuid 검증·원자 해제·재진입을 라이브러리가 알아서 처리한다.

주의점(여기가 본론):

- **TTL보다 작업이 길어지면** 락이 풀려 두 주체가 동시 진입 → Redisson의 **watchdog**(자동 연장) 또는 작업을 TTL 안으로.
- **failover 유실**: 비동기 복제라 마스터 죽으면 락 정보가 사라져 **두 클라이언트가 동시에 락을 가질 수 있다.**
- **Redlock**: 독립 노드 N대 과반 획득으로 보완하려는 알고리즘이지만, 시계 점프·GC pause 문제로 **안전성 논쟁**(Kleppmann 비판)이 유명. 결론 — **효율성용**(중복 작업 방지)이면 Redis 락으로 충분, **정확성이 생명**(돈·재고)이면 **DB 제약/버전(낙관락)이나 fencing token**으로 최종 방어선을 두라.

**꼬리질문**: fencing token이 뭔가요?
→ 락 획득마다 **단조 증가 토큰**을 발급받고, 보호 자원(DB 등)이 **더 낮은 토큰의 쓰기를 거부**하는 것. 락이 잘못 풀려 둘이 들어와도 최종 자원에서 구버전 쓰기를 차단 — "락을 믿지 말고 자원에서 검증".

**감점 포인트**: `SETNX`만 말하고 끝. 내 락 검증·TTL 연장·failover 한계 중 두 개 이상 빠지면 실무에서 사고 낼 답. (반대로 직접 구현을 고집하는 것도 감점 — 검증된 라이브러리를 쓰는 게 답이다.)

> **분산락 흐름** — SET NX PX 획득 → uuid 검증 → 해제, 그리고 TTL 연장·failover 한계.

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
    edgeLabelBackground: "#ffffff"
---
flowchart LR
  subgraph canvas[" "]
    direction TB

    subgraph acquire["락 획득"]
      direction TB
      AC1["클라이언트"]
      AC2["SET lock {uuid} NX PX 5000"]
      AC3@{ img: "https://icons.terrastruct.com/dev/redis.svg", label: "락 획득 성공", pos: "b", h: 48, constraint: "on" }
      AC1 --> AC2 --> AC3
    end

    subgraph work["작업 수행"]
      direction TB
      WK1["임계구역 실행"]
      WK2["TTL 내 완료?\n(길어지면 watchdog 연장)"]
      WK1 --> WK2
    end

    subgraph release["락 해제"]
      direction TB
      RL1["uuid 검증 (내 락인지 확인)"]
      RL2["원자적 삭제\n(Redisson RLock)"]
      RL3@{ img: "https://icons.terrastruct.com/dev/redis.svg", label: "락 해제 완료", pos: "b", h: 48, constraint: "on" }
      RL1 --> RL2 --> RL3
    end

    subgraph risk["주의점"]
      direction TB
      RS1["TTL < 작업 시간\n→ 두 주체 동시 진입"]
      RS2["failover 유실\n→ 비동기 복제로 락 소실"]
    end

    AC3 --> WK1
    WK2 --> RL1
  end

  classDef app fill:#EFF6FF,stroke:#3B5BA5,stroke-width:1px,color:#16213E
  classDef db fill:#F0FDF4,stroke:#3F8E55,stroke-width:1px,color:#14532D
  classDef ctrl fill:#FFF7ED,stroke:#C98A2B,stroke-width:1px,color:#7A4E0A
  classDef icon fill:transparent,stroke:transparent,stroke-width:0px,color:#111827

  class AC1,WK1 app
  class AC3,RL3 icon
  class AC2,RL1,RL2 db
  class WK2,RS1,RS2 ctrl

  style acquire fill:#F8FAFC,stroke:#CBD5E1,stroke-width:1px,color:#111827
  style work fill:#F8FAFC,stroke:#CBD5E1,stroke-width:1px,color:#111827
  style release fill:#F8FAFC,stroke:#CBD5E1,stroke-width:1px,color:#111827
  style risk fill:#FEF2F2,stroke:#FCA5A5,stroke-width:1px,color:#991B1B
  style canvas fill:#ffffff,stroke:#ffffff,stroke-width:0px,color:#111827
```

### Q10. Redis 트랜잭션(MULTI/EXEC)은 RDB 트랜잭션과 어떻게 다른가요?

**모범답안**: 이름만 같고 다른 물건이다.

- MULTI/EXEC는 **명령 큐잉 + 원자적 일괄 실행**일 뿐: **롤백이 없다**(중간 실패해도 나머지 실행), 격리 수준 개념도 없다.
- 조건부 실행은 **WATCH**(낙관락) — 감시 키가 바뀌면 EXEC가 무효.
- 애플리케이션 개발자 관점의 우선순위: ① 애초에 **단일 원자 명령으로 풀리게 설계**(`INCR`, `SET NX EX`, ZSET 연산 등 — Redis 명령 하나는 그 자체로 원자) ② 여러 명령을 원자로 묶어야 하면 **그걸 내장한 검증된 라이브러리**(Redisson 락, Bucket4j 리미터). 서버 사이드 스크립트(Lua)로 직접 짜는 건 라이브러리가 없을 때의 최후 수단이고, 스크립트도 싱글 스레드를 점유한다는 비용이 있다.

**감점 포인트**: "Redis도 트랜잭션 있으니 ACID 됨"이라는 오해. 롤백 없음을 모르면 위험.

## 레벨 4 — Spring & 게이트웨이 맥락

### Q11. Spring에서 Redis 쓸 때 흔한 함정은?

**모범답안**:

- **직렬화**: `RedisTemplate` 기본이 JDK 직렬화 — 바이너리라 다른 언어/콘솔에서 못 읽고 클래스 변경에 깨진다 → **`StringRedisSerializer` + JSON**(GenericJackson2 등)으로 명시.
- **클라이언트**: 기본 **Lettuce**(netty, 스레드세이프, 비동기) vs Jedis(커넥션당 스레드, 풀 필수). 특별한 이유 없으면 Lettuce.
- **`@Cacheable`**: TTL 기본이 **무제한** — `RedisCacheConfiguration`으로 TTL 명시 안 하면 영원히 쌓인다. 캐시 이름별 TTL 분리.
- **타임아웃**: command timeout(Lettuce 기본 60s)도 다운스트림 타임아웃이다 — [타임아웃 문서](./260610-모의면접-timeout.md)의 outbound 원칙 그대로(짧게, 명시적으로).
- 트래픽 많은 키 조회는 **로컬 캐시(Caffeine) 1차 + Redis 2차** 다층이 정석.

**감점 포인트**: `@Cacheable` 붙이면 끝이라는 답. 직렬화·TTL 기본값을 모르면 실제로 안 써본 것.

### Q12. LLM 게이트웨이를 만든다면 Redis를 어디에 쓰겠습니까?

**모범답안**: 게이트웨이의 **공유 상태 저장소**로 거의 모든 횡단 관심사에 등장한다.

- **Rate limiting**: 키당 카운터. 고정 윈도우는 `INCR`+`EXPIRE`, 정밀하게는 **sliding window**(ZSET에 타임스탬프, 범위 카운트)나 token bucket — 직접 짜지 말고 **Bucket4j·Redisson 같은 검증된 구현**을 쓴다. 분산 인스턴스가 한도를 공유하려면 Redis가 사실상 표준.
- **Idempotency key 저장**: 타임아웃 재시도의 중복 실행 방지(→ [타임아웃 Q5](./260610-모의면접-timeout.md)). `SET key result NX EX 86400`.
- **응답 캐시**: 동일 prompt+모델+파라미터의 **exact-match 캐시**(해시 키). 한 발 더 가면 임베딩 유사도 기반 **semantic cache**(Redis 벡터 검색).
- **사용량/비용 집계**: 테넌트별 토큰 카운터(`INCRBY`), 일별 버킷 키 + TTL.
- **서킷브레이커/헬스 상태 공유**: 인스턴스 여러 대가 프로바이더 상태를 공유.

**꼬리질문**: rate limiter를 `INCR`+`EXPIRE` 두 명령으로 짜면 무슨 문제?
→ 두 명령 사이가 원자적이지 않다 — INCR 후 앱이 죽으면 **TTL 없는 카운터가 영원히 남는다.** 이 원자성 문제를 이미 풀어둔 검증된 구현(Bucket4j, Redisson)을 쓰는 게 답. "명령 두 개를 이으면 원자성이 깨진다"는 감각 자체가 포인트다.

**감점 포인트**: "캐시로 쓴다" 한 줄. 게이트웨이 횡단 관심사(rate limit·멱등성·집계)로 연결 못 하면 설계 경험 부족.

## 한 장 요약 (면접 직전 복습용)

```
1. 빠른 이유: 인메모리 + 싱글스레드 이벤트루프 + 자료구조. (6+는 I/O만 멀티스레드)
2. 싱글스레드의 덫: KEYS·big key·O(N) 명령이 전체 블로킹 → SCAN/UNLINK/분할
3. 자료구조가 설계다: ZSET=랭킹/슬라이딩윈도우, Hash=객체, HLL=유니크, Streams=큐
4. 캐시 쓰기: DB 갱신 후 "삭제"(update 아님) + TTL 안전망
5. 3대 장애: stampede(락/logical expire) · penetration(null캐시/bloom) · avalanche(TTL jitter/다층)
6. eviction 기본값 noeviction → 캐시면 allkeys-lru로. 만료는 lazy+active(즉시 아님)
7. 영속성: RDB(fork·COW 스파이크) vs AOF(everysec=최대 1초 유실). 어쨌든 source of truth 금지
8. Sentinel=HA / Cluster=샤딩(16384 slot, 멀티키=hash tag). 복제는 비동기 → failover 유실
9. 분산락: SET NX PX + 내 락 검증 → 실무는 Redisson RLock. 효율성용은 OK, 정확성은 fencing token/DB로
10. MULTI/EXEC는 롤백 없음. 단일 원자 명령으로 설계 → 안 되면 검증된 라이브러리
11. Spring: 직렬화 명시(JSON), Lettuce, @Cacheable TTL 필수, command timeout 명시
12. 게이트웨이: rate limit(Bucket4j/Redisson) · idempotency key · 응답/semantic 캐시 · 사용량 집계
```

> 떨어지는 답 vs 붙는 답: 기능 나열이 아니라 **"어디서 무너지는가"**(블로킹·stampede·failover 유실·락의 한계)를 먼저 말하는 것.
