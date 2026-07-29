# 메시지 큐(MQ)

메시지 큐(MQ)는 프로듀서가 메시지를 발행하고, 브로커가 저장·전달하며, 컨슈머가 소비하는 비동기 분리 구조다. 핵심은 "언제 메시지가 유실되고, 언제 중복되며, 순서가 언제 깨지는가"를 아는 것.

## 구현체 비교 — Postgres as Queue · RabbitMQ · SQS/SNS · Kafka

| | Postgres as Queue (DB 기반) | RabbitMQ | SQS/SNS | Kafka |
|---|---|---|---|---|
| 모델 | RDBMS 테이블 + FOR UPDATE SKIP LOCKED | 전통적 AMQP, exchange→queue 라우팅 | 매니지드 큐(SQS) + pub/sub(SNS) | 분산 append-only 로그 |
| 소비 방식 | **polling** (폴링) — 컨슈머가 주기적으로 쿼리 | **push** — 브로커가 컨슈머에게 밀어넣음 | **polling** — 컨슈머가 주기적으로 pull | **polling** — 컨슈머가 offset을 올리며 pull |
| 순서 보장 | `ORDER BY`로 글로벌 순서 가능. 단, `SKIP LOCKED`는 순서 무시 | 단일 큐 내 FIFO (consumer 1개일 때만) | FIFO 큐 별도 (표준 큐는 순서 보장 X) | 파티션 내 순서 보장. 파티션이 여러 개면 파티션 간 순서는 X |
| 전달 보장 | **at-least-once** (sweeper가 stuck row 재처리) | **at-least-once** (ack 실패 시 재전송) | **at-least-once** (visibility timeout 후 재전송) | **at-least-once** (consumer offset). exactly-once는 트랜잭션(EOS) 옵션 |
| 메시지 보관 | 직접 관리 (status update 또는 DELETE) | 소비 후 삭제 | 소비 후 삭제 | 보관 기간 유지 (retention) |
| 중복 제거 | UNIQUE 제약 + 멱등키 | 애플리케이션 책임 | FIFO 큐는 content-based dedup | idempotent producer |
| retry/DLQ | 수동 구현 (retry_count, status 컬럼) | DLX(Dead Letter Exchange) 내장 | SQS DLQ 연결 | DLQ 직접 구성 |
| 지연 큐 | visible_at 컬럼 + 폴링 조건 | plugin / TTL + DLX | 지연 큐(DelaySeconds) | 지원 안 함 (직접 구현) |
| 확장 | 읽기 복제만, 쓰기 단일 노드 | 클러스터·shovel·federation | AWS가 관리 | 파티션 추가 |
| 장점 | 트랜잭션 원자성, 외부 의존성 없음 | 라우팅 유연성, retry/DLQ 추상화 | 운영 부담 제로 | 높은 처리량, replay 가능 |
| 단점 | 처리량 한계, lock 경합, VACUUM 압박 | 운영 부담, 클러스터 복잡 | 벤더 종속, 세밀 제어 한계 | 무겁다, 운영 복잡 |

```mermaid
---
config:
  theme: base
  darkMode: false
  look: classic
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
    direction LR

    subgraph pg["Postgres as Queue"]
      direction TB
      pgProd["Producer"]:::app
      pgTable["queue_table\n(FOR UPDATE SKIP LOCKED)"]:::db
      pgProd --> pgTable
      pgTable --> pgC1["Consumer A"]:::app
      pgTable --> pgC2["Consumer B"]:::app
      pgTable --> pgC3["Consumer C"]:::app
    end

    subgraph rabbit["RabbitMQ"]
      direction TB
      rProd["Producer"]:::app
      rRabbit@{ img: "https://cdn.simpleicons.org/rabbitmq", label: "", pos: "b", h: 48, constraint: "on" }
      rQueue["Queue"]:::db
      rProd --> rRabbit
      rRabbit --> rQueue
      rQueue --> rC1["Consumer A"]:::app
      rQueue --> rC2["Consumer B"]:::app
      rQueue --> rC3["Consumer C"]:::app
    end

    subgraph sns["SNS + SQS"]
      direction TB
      sProd["Producer"]:::app
      snsTopic@{ img: "https://icons.terrastruct.com/aws/Application%20Integration/Amazon-Simple-Notification-Service-SNS_light-bg.svg", label: "", pos: "b", h: 48, constraint: "on" }
      sqs1@{ img: "https://icons.terrastruct.com/aws/Application%20Integration/Amazon-Simple-Queue-Service-SQS_light-bg.svg", label: "SQS", pos: "b", h: 48, constraint: "on" }
      sProd --> snsTopic
      snsTopic --> sqs1
      sqs1 --> sC1["Consumer A"]:::app
      sqs1 --> sC2["Consumer B"]:::app
      sqs1 --> sC3["Consumer C"]:::app
    end

    subgraph kafka["Kafka"]
      direction TB
      kProd["Producer\n(key=routing)"]:::app
      kTopic@{ img: "https://cdn.simpleicons.org/apachekafka", label: "Topic", pos: "b", h: 48, constraint: "on" }
      kPart1["Partition 0"]:::db
      kPart2["Partition 1"]:::db
      kProd --> kTopic
      kTopic --> kPart1
      kTopic --> kPart2
      kPart1 --> kC1["Consumer A\n(partition 0)"]:::app
      kPart2 --> kC2["Consumer B\n(partition 1)"]:::app
      kC3["Consumer C\n(idle)"]:::app
    end

    pg ~~~ rabbit ~~~ sns ~~~ kafka
  end

  classDef icon fill:transparent,stroke:transparent,stroke-width:0px,color:#111827
  classDef app fill:#EFF6FF,stroke:#3B5BA5,stroke-width:1px,color:#16213E
  classDef db fill:#F0FDF4,stroke:#3F8E55,stroke-width:1px,color:#14532D
  classDef ctrl fill:#FFF7ED,stroke:#C98A2B,stroke-width:1px,color:#7A4E0A
  class rRabbit,snsTopic,sqs1,kTopic icon
  class pgProd,pgC1,pgC2,pgC3,rProd,rC1,rC2,rC3,sProd,sC1,sC2,sC3,kProd,kC1,kC2,kC3 app
  class pgTable,rQueue,kPart1,kPart2 db
  style pg fill:#F8FAFC,stroke:#CBD5E1,stroke-width:1px,color:#111827
  style rabbit fill:#F8FAFC,stroke:#CBD5E1,stroke-width:1px,color:#111827
  style sns fill:#F8FAFC,stroke:#CBD5E1,stroke-width:1px,color:#111827
  style kafka fill:#F8FAFC,stroke:#CBD5E1,stroke-width:1px,color:#111827
  style canvas fill:#ffffff,stroke:#ffffff,stroke-width:0px,color:#111827
```

## 메시징 발전 단계 — 인메모리 → Postgres → 외부 MQ

외부 MQ를 처음부터 도입하는 팀은 없다.  
요구사항이 바뀌면서 단계를 밟게 된다.  
각 단계는 "영속성 · 원자성 · 처리량" 3축의 트레이드오프다.

> 각 단계는 필수 경로가 아니다.  
> 트래픽 특성에 따라 건너뛸 수 있으며,  
> Postgres as Queue에서 영구 정착하는 팀도 많다.

### 3축 비교 — 영속성 · 원자성 · 처리량

| 구현 | 영속성 | 원자성 (비즈니스와의) | 처리량 | 운영 복잡도 | 적합 시점 |
|---|---|---|---|---|---|
| Spring Application Events | ✗ | ✗ | 높음 (JVM 내) | 낮음 | 단일 JVM 도메인 이벤트 |
| Postgres as Queue | ✓ (WAL) | ✓ (트랜잭션) | ~수백 TPS | 낮음~중간 | 외부 MQ 도입 전, 원자성 중요 |
| RabbitMQ | ✓ (브로커) | 분리 (Outbox 필요) | ~수만 TPS | 중간 | 라우팅·워크큐 |
| Kafka | ✓ (로그) | 분리 (Outbox 필요) | ~수십만 TPS | 높음 | 고처리량 스트리밍 |

```mermaid
---
config:
  theme: base
  darkMode: false
  look: classic
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
    direction LR
    mem["1. 인메모리\nApplicationEventPublisher\n영속성 ✗ · 원자성 ✗"]:::app
    pg["2. Postgres as Queue\nFOR UPDATE SKIP LOCKED\n영속성 ✓ · 원자성 ✓ · ~수백 TPS"]:::db
    mq["3. 외부 MQ\nRabbitMQ · Kafka\n영속성 ✓ · 수만~수십만 TPS"]:::ctrl

    mem --> pg --> mq
  end

  classDef app fill:#EFF6FF,stroke:#3B5BA5,stroke-width:1px,color:#16213E
  classDef db fill:#F0FDF4,stroke:#3F8E55,stroke-width:1px,color:#14532D
  classDef ctrl fill:#FFF7ED,stroke:#C98A2B,stroke-width:1px,color:#7A4E0A
  style canvas fill:#ffffff,stroke:#ffffff,stroke-width:0px,color:#111827
```

### 인메모리 — Spring ApplicationEventPublisher

같은 JVM 내에서 이벤트를 발행하고 수신한다.  
앱이 죽으면 메시지가 유실된다 — 영속성이 없다.

```kotlin
// 이벤트 발행
applicationEventPublisher.publishEvent(OrderCompletedEvent(orderId))

// 이벤트 수신
@EventListener
fun handle(event: OrderCompletedEvent) {
    notificationService.send(event.orderId)
}
```

- **장점**: 외부 인프라 없음, 구조가 단순, 지연 최소
- **한계**: 앱 크래시 = 메시지 유실, 다중 인스턴스 시 중복 처리 위험
- **적합**: 알림·로그 등 유실 허용되는 도메인 이벤트

### Postgres as Queue — FOR UPDATE SKIP LOCKED

Postgres 테이블 자체를 큐로 쓴다.  
비즈니스 트랜잭션과 메시지 저장이 원자적으로 묶인다는 것이 핵심 장점이다.

#### 핵심 쿼리 — FOR UPDATE SKIP LOCKED

```sql
-- 여러 컨슈머가 동시에 폴링해도 서로 다른 행을 가져간다
SELECT id, payload FROM queue_table
WHERE status = 'pending' AND visible_at <= now()
ORDER BY created_at
FOR UPDATE SKIP LOCKED
LIMIT 10;
```

`SKIP LOCKED`는 이미 다른 컨슈머가 잡고 있는 행을 건너뛴다.  
여러 컨슈머가 병렬로 폴링해도 충돌 없이 각자 다른 메시지를 처리한다.

```mermaid
---
config:
  theme: base
  darkMode: false
  look: classic
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
    direction LR

    prod["Producer\nINSERT INTO queue_table"]:::app

    subgraph queue["queue_table"]
      direction TB
      row1["row 1\nstatus=pending"]:::db
      row2["row 2\nstatus=processing\n(visible_at +30s)"]:::db
      row3["row 3\nstatus=pending"]:::db
      row4["row 4\nstatus=done"]:::db
    end

    poll["SELECT ...\nFOR UPDATE SKIP LOCKED"]:::ctrl
    c1["Consumer A\n→ row 1"]:::app
    c2["Consumer B\n→ row 3"]:::app
    sweep["sweeper\nrow 2 타임아웃 시\n→ pending 복구"]:::ctrl

    prod --> row1
    row1 --> poll
    poll --> c1
    poll --> c2
    sweep --> row2
  end

  classDef app fill:#EFF6FF,stroke:#3B5BA5,stroke-width:1px,color:#16213E
  classDef db fill:#F0FDF4,stroke:#3F8E55,stroke-width:1px,color:#14532D
  classDef ctrl fill:#FFF7ED,stroke:#C98A2B,stroke-width:1px,color:#7A4E0A
  style queue fill:#F8FAFC,stroke:#CBD5E1,stroke-width:1px,color:#111827
  style canvas fill:#ffffff,stroke:#ffffff,stroke-width:0px,color:#111827
```

#### partial index — 없으면 풀 스캔 + lock 경합

```sql
-- 필수: status='pending'인 행만 인덱싱
CREATE INDEX idx_queue_pending ON queue_table (created_at)
WHERE status = 'pending';
```

partial index가 없으면 폴링 쿼리가 테이블 전체를 스캔하면서  
모든 행에 lock을 시도한다 → 동시 컨슈머가 경합 → 처리량 급락.  
Postgres as Queue에서 가장 흔한 실수다.

#### visibility timeout 구현

SQS의 `visibility_timeout`을 PG로 직접 구현한다.  
컨슈머가 메시지를 가져간 후 처리 중 죽었을 때 재처리를 보장한다.

```sql
-- 메시지 확보 (visibility timeout 30초)
UPDATE queue_table
SET status = 'processing', visible_at = now() + interval '30 seconds'
WHERE id IN (
  SELECT id FROM queue_table
  WHERE status = 'pending' AND visible_at <= now()
  ORDER BY created_at
  FOR UPDATE SKIP LOCKED
  LIMIT 10
)
RETURNING *;

-- 처리 완료
UPDATE queue_table SET status = 'done', processed_at = now()
WHERE id = $1;
```

#### sweeper — stuck row 회수

컨슈머가 `processing` 상태로 가져간 후 hang되면,  
`visible_at`이 지나도 `status`가 `processing`인 채로 갇힌다.  
별도 sweeper 프로세스가 주기적으로 회수한다.

```sql
-- 타임아웃된 메시지 재큐잉 (30초마다 실행)
UPDATE queue_table
SET status = 'pending', visible_at = now(), retry_count = retry_count + 1
WHERE status = 'processing' AND visible_at < now();
```

> visibility timeout + sweeper 조합이 at-least-once를 보장한다.  
> 컨슈머 크래시 → 롤백 → 락 해제 → 재처리 (OK).  
> 컨슈머 hang (연결 살아있음) → 락이 안 풀림 → sweeper가 회수.

#### LISTEN/NOTIFY — 보조 트리거, 주력이 아님

`LISTEN/NOTIFY`로 폴링 주기를 보완할 수 있다.  
하지만 **신뢰할 수 없으므로 보조 수단이다**:

- **유실 가능성**: listener가 연결 끊김 중 발생한 NOTIFY는 사라진다
- **payload 제한**: 8,000 bytes
- **재전송 없음**: at-least-once 보장이 안 된다
- **커넥션 풀 점유**: LISTEN 중인 커넥션은 풀에서 빠져 전용으로 점유된다

정석 패턴: **폴링이 주력, LISTEN/NOTIFY는 idle 시 wake-up용 보조**.  
LISTEN/NOTIFY가 유실돼도 폴백 폴링(30초 주기)이 있으니 at-least-once가 보장된다.

#### VACUUM 압박 — soft delete vs DELETE

```sql
-- 나쁜 패턴: 매 소비마다 DELETE → dead tuple 폭증
DELETE FROM queue_table WHERE id = $1;

-- 나은 패턴: status update + 주기적 cleanup
UPDATE queue_table SET status = 'done', processed_at = now() WHERE id = $1;
-- 주기적: DELETE FROM queue_table WHERE status = 'done'
--   AND processed_at < now() - interval '7 days';
```

DELETE가 매 소비마다 발생하면 dead tuple이 피크 시간에 집중된다.  
autovacuum이 따라가지 못하면 테이블 bloat → 디스크 압박.

soft delete(`status = 'done'`) + batch purge가 나은 이유는 dead tuple이 줄어서가 아니다 —  
UPDATE도 dead tuple을 1개 만든다 (Postgres MVCC).  
차이는 **삭제 시점 통제**에 있다:

| | 매번 DELETE | soft delete + batch purge |
|---|---|---|
| dead tuple 발생 시점 | 소비 타이밍 (피크) | purge 타이밍 (한산한 시간) |
| VACUUM 부담 | 소비 속도에 비례 | purge 주기로 통제 |
| 이력 보존 | 즉시 삭제 | `done` 행으로 추적 가능 |

#### JPA / Exposed 코드 예시

```kotlin
// JPA — FOR UPDATE SKIP LOCKED는 JPA 표준이 아니므로 native query 사용
@Query(
    value = """
        SELECT * FROM queue_table
        WHERE status = 'pending' AND visible_at <= :now
        ORDER BY created_at
        FOR UPDATE SKIP LOCKED
        LIMIT :limit
        """,
    nativeQuery = true
)
List<QueueEntity> findPendingForUpdate(
    @Param("now") Instant now,
    @Param("limit") Int limit
)
```

```kotlin
// Exposed — forUpdate(SkipLocked) 로 직접 지원
transaction {
    QueueTable
        .select {
            (QueueTable.status eq "pending") and
            (QueueTable.visibleAt lessEq now())
        }
        .orderBy(QueueTable.createdAt to SortOrder.ASC)
        .forUpdate(SkipLocked)
        .limit(10)
        .toList()
}
```

Exposed는 `forUpdate(SkipLocked)`로 `SKIP LOCKED`를 직접 지원한다.

### Outbox vs Postgres as Queue — 개념 구분

두 패턴은 모두 Postgres을 사용하지만, **구조적으로 다른 결정**이다.

| | Outbox 패턴 | Postgres as Queue |
|---|---|---|
| Postgres 역할 | 임시 대기소 (relay target) | 최종 큐 (최종 목적지) |
| 외부 MQ | 있음 (Postgres → MQ로 relay) | 없음 (Postgres이 곧 큐) |
| 해결 문제 | Dual-write (DB + MQ 원자적 쓰기) | 외부 MQ 도입 비용 회피 |
| 데이터 흐름 | App → Postgres(outbox) → relay → MQ → consumer | App → Postgres(queue) → consumer |
| 트랜잭션 | 비즈니스 쓰기 + outbox 쓰기 동일 트랜잭션 | 큐 행 insert가 별도 트랜잭션 |

> Outbox는 Postgres이 외부 MQ로의 릴레이 창구다.  
> Postgres as Queue는 Postgres 자체가 큐다.  
"외부 MQ를 도입할 것인가?"에 대한 다른 답이다.

### 외부 MQ로 넘어가는 시점

Postgres as Queue의 한계가 체감되면 외부 MQ를 검토한다:

- 폴링 쿼리의 p99 지연이 서비스 SLO 위반
- Postgres 커넥션 풀 고갈 빈도 증가
- VACUUM 후에도 dead tuple 비율이 20% 이상
- sweeper가 stuck row를 정리하는 빈도 증가
- 동시 컨슈머 증가 시 lock 경합으로 처리량 정체

> 실무에서는 처음부터 외부 MQ를 도입하지 않는다.  
> Postgres as Queue로 시작하고, 한계가 보이면 RabbitMQ(워크큐) 또는 Kafka(스트리밍)로 전환한다.

> **실무 팁 — Redis를 경량 큐로 쓰는 경우**  
> `BRPOP`/`LPUSH`로 Redis 리스트 기반 큐를 만드는 패턴이 있다.  
> Sidekiq(Ruby), Celery(Python), Bull(Node.js)에서 사실상 표준이다.  
> 인메모리와 Postgres 사이의 가벼운 중간 단계로 적합하지만,  
> 비즈니스 DB 쓰기와 Redis 큐 쓰기가 원자적이지 않다 (dual-write 문제).  
> 영속성은 RDB/AOF로 부분 보장되나, 트랜잭션 원자성이 필요하면 Postgres as Queue가 낫다.

## 용어 정리 — produce/publish vs consume/subscribe

| 개념 | 발행 측 | 소비 측 | 비고 |
|---|---|---|---|
| **전통적 MQ** (RabbitMQ) | publish | consume | exchange→queue→consumer |
| **Kafka** | produce | consume (poll) | topic→partition→consumer group |
| **SQS** | send | receive (poll) | 큐 중심, pull 모델 |
| **SNS** | publish | subscribe | fan-out 전용, SQS/HTTP/Lambda 구독 |

- **publish/produce**: 메시지를 브로커에 보내는 행위. "브로커에 도달했다"이지 "컨슈머가 받았다"는 아니다.
- **consume/subscribe**: consume은 메시지를 가져오는 행위. subscribe는 관심 등록(poll/receive의 선행). Kafka에선 consumer group이 topic을 subscribe하고, 파티션별 poll로 consume.
- **deliver**: 브로커가 컨슈머에게 메시지를 전달하는 행위. ack 전에는 "전달됐지만 처리 안 됨" 상태.

## 1. 결과적 일관성 (Eventual Consistency)

MQ를 쓴다는 건 "지금 즉시 일치하지 않아도, 언젠가 수렴하면 OK"로 설계하는 것이다.

### 언제 결과적 일관성으로 가나

- 한 트랜잭션에 묶기엔 관계가 너무 많거나, 외부 시스템 호출이 포함될 때
- 사용자 응답 지연을 피하기 위해 비동기로 분리할 때
- 장애 격리: 주문은 성공시키고, 알림·재고·분석은 별도로 처리

### 트랜잭션과 발행의 원자성 문제

DB 커밋과 MQ 발행은 분리된 두 작업이다. 둘 중 하나만 성공하면 문제가 된다:

| 시나리오 | 결과 |
|---|---|
| DB 커밋 후 MQ 발행 실패 | 커밋됐는데 이벤트 유실 → downstream이 모름 |
| MQ 발행 후 DB 커밋 실패 | 이벤트는 갔는데 DB는 안 됨 → 헛발사 |
| DB 커밋 직후 프로세스 죽음 | 커밋됐는데 발행 못 함 |

**해결: Transactional Outbox**

```
1. 비즈니스 데이터 + outbox 이벤트를 같은 DB 트랜잭션에 INSERT
2. 별도 프로세스(poller/CDC)가 outbox 테이블을 읽어 MQ로 발행
3. 발행 성공 시 outbox 행을 'published'로 마킹 (또는 삭제)
```

같은 트랜잭션이라 DB 원자성이 보장되고, 발행은 별도 프로세스가 재시도한다. Kafka용으로는 Debezium CDC 연동이 대표적.

> outbox 패턴에서 poller가 outbox 테이블을 읽을 때 `FOR UPDATE SKIP LOCKED`를 쓰는 것이 정석이다.  
> [Postgres as Queue](#postgresql-as-queue--for-update-skip-locked) 섹션과 비교하라 —  
> Outbox는 Postgres이 외부 MQ로의 릴레이 창구고, Postgres as Queue는 Postgres 자체가 최종 큐다.

```mermaid
---
config:
  theme: base
  darkMode: false
---
flowchart LR
  subgraph canvas[" "]
    direction LR
    subgraph tx["DB 트랜잭션 (원자성 보장)"]
      direction TB
      app["애플리케이션"]:::app
      pg@{ img: "https://icons.terrastruct.com/dev/postgresql.svg", label: "Postgres", pos: "b", h: 48, constraint: "on" }
      bizTable["비즈니스 테이블\n(orders 등)"]:::db
      outboxTable["outbox 테이블\n(event, status=pending)"]:::db
      app --> pg
      pg --> bizTable
      pg --> outboxTable
    end

    poller["Poller / CDC\n(별도 프로세스, 발행 성공 시 published)"]:::ctrl
    broker@{ img: "https://cdn.simpleicons.org/apachekafka", label: "MQ / Kafka", pos: "b", h: 48, constraint: "on" }
    downstream["Consumer\n(downstream 서비스)"]:::app

    outboxTable --> poller
    poller --> broker
    broker --> downstream
  end

  classDef icon fill:transparent,stroke:transparent,stroke-width:0px,color:#111827
  classDef app fill:#EFF6FF,stroke:#3B5BA5,stroke-width:1px,color:#16213E
  classDef db fill:#F0FDF4,stroke:#3F8E55,stroke-width:1px,color:#14532D
  classDef ctrl fill:#FFF7ED,stroke:#C98A2B,stroke-width:1px,color:#7A4E0A
  class pg,broker icon
  style tx fill:#F8FAFC,stroke:#CBD5E1,stroke-width:1px,color:#111827
  style canvas fill:#ffffff,stroke:#ffffff,stroke-width:0px,color:#111827
```

> outbox를 안 쓰면 "DB 커밋 후 send"가 되는데, 이때 send 실패는 애플리케이션에서 catch해서 재시도하거나 보상해야 한다. 완벽하지 않으므로 중요한 도메인은 outbox가 정석.

## 2. 메시지 전달 보장 (At-least-once 등)

### 세 가지 전달 보장

| 보장 | 의미 | 유실 | 중복 | 실무 |
|---|---|---|---|---|
| **at-most-once** | 최대 1회 | 가능 | 없음 | 로그, metrics (유실 돼도 OK) |
| **at-least-once** | 최소 1회 | 없음 | 가능 | **대부분의 실무** |
| **exactly-once** | 정확히 1회 | 없음 | 없음 | Kafka 트랜잭션 (비용 큼) |

실무의 기본은 **at-least-once**다. 중복이 발생할 수 있으므로 **컨슈머 멱등성이 필수**.

```mermaid
---
config:
  theme: base
  darkMode: false
---
flowchart LR
  subgraph canvas[" "]
    direction LR
    broker@{ img: "https://cdn.simpleicons.org/apachekafka", label: "Broker", pos: "b", h: 48, constraint: "on" }
    consumer["Consumer"]:::app
    process["비즈니스 처리"]:::app
    ack["ack / offset commit"]:::ctrl
    crash["💥 크래시\n(ack 직전 실패)"]:::ctrl
    redeliver["동일 메시지 재전달"]:::app
    idempotent{"멱등성\n체크"}:::ctrl
    skip["중복 무시\n(이미 처리됨)"]:::ctrl

    broker --> consumer
    consumer --> process
    process --> ack
    ack --> crash
    crash --> broker
    broker --> redeliver
    redeliver --> idempotent
    idempotent --> skip
  end

  classDef icon fill:transparent,stroke:transparent,stroke-width:0px,color:#111827
  classDef app fill:#EFF6FF,stroke:#3B5BA5,stroke-width:1px,color:#16213E
  classDef ctrl fill:#FFF7ED,stroke:#C98A2B,stroke-width:1px,color:#7A4E0A
  class broker icon
  style canvas fill:#ffffff,stroke:#ffffff,stroke-width:0px,color:#111827
```

### 구현체별 전달 보장

| | RabbitMQ | SQS | Kafka |
|---|---|---|---|
| 기본 | at-least-once (ack 기반) | at-least-once (delete on receive) | at-least-once (offset commit 기반) |
| ack 전 | 메시지는 unacked 상태로 보존 | visibility timeout 내 재전달 가능 | offset commit 전에는 재소비 가능 |
| ack 후 | 메시지 삭제 | 메시지 삭제 | offset 전진, 다시 못 읽음 (retention 내 offset 이동) |
| 중복 원인 | ack 유실·네트워크 | visibility timeout 만료 후 재전달 | commit 전 크래시 → 재소비 |
| exactly-once | 미지원 | 미지원 (FIFO도 at-least-once) | idempotent producer + 트랜잭션 |

### 컨슈머 멱등성 (Idempotent Consumer)

at-least-once에서 중복을 제거하는 방법:

1. **멱등키**: 메시지에 고유 ID(`message_id` 또는 비즈니스 키)를 두고, 처리 전 저장소(DB/Redis)에서 이미 처리했는지 확인.
2. **DB 유니크 제약**: `INSERT ... ON CONFLICT DO NOTHING`으로 중복 insert 차단.
3. **상태 머신**: 이미 처리된 상태로 가는 전이는 무시. (예: `PAID → PAID` 무시)

```java
// 멱등키 + 상태 머신 조합 예시
@Transactional
public void handlePaymentPaid(PaymentPaidEvent event) {
    Payment payment = paymentRepository.findById(event.paymentId())
        .orElseThrow();
    if (payment.getStatus() == PaymentStatus.PAID) {
        return; // 이미 처리됨 — 멱등 보장
    }
    payment.markAsPaid();
}
```

### Kafka exactly-once

- **Idempotent Producer**: `enable.idempotence=true`. PID + 시퀀스 번호로 브로커가 중복을 제거. 같은 파티션 내 중복 방지.
- **트랜잭션**: consume → process → produce를 원자로 묶음. `transactional.id` 지정. `commit` 전까지 downstream 컨슈머는 `isolation_level=read_committed`로 uncommitted 메시지를 안 봄.
- 비용이 크므로 "반드시 정확히 한 번"이 필요한 결제·재고에만 제한 적용.

## 3. 순서 보장 방법론

순서는 "어떤 단위로 보장하느냐"가 핵심이다. 글로벌 순서는 병렬성을 포기해야 얻는다.

### 순서 단위와 보장 방법

| 구현체 | 순서 단위 | 보장 조건 | 깨지는 경우 |
|---|---|---|---|
| RabbitMQ | 큐 1개 | consumer 1개 | consumer 다수 / prefetch + 재시도 |
| SQS FIFO | message group | 같은 group ID | group별 독립 처리 |
| Kafka | 파티션 | 같은 key = 같은 파티션 | 파티션 수 변경 / 컨슈머 병렬 처리 |

### 실무 패턴 — "무엇을 key로 잡을 것인가"

순서 보장의 출발점은 key 선택이다. key가 곧 "순서 단위"다.

| 도메인 | key | 순서가 중요한 이유 |
|---|---|---|
| 주문 상태 전이 | `order_id` | CREATED → PAID → SHIPPED 순서가 바뀌면 안 됨 |
| 결제 이벤트 | `payment_id` | 승인 → 취소 순서 보장 |
| 사용자 활동 | `user_id` | 프로필 변경 이벤트 순서 |
| 게임 상태 | `player_id` | 이동·공격 순서 |

- key를 안 주면 round-robin으로 파티션이 분산되어 순서 보장이 사라진다.
- key를 너무 좁게 잡으면(예: `order_id + item_id`) 한 주문의 이벤트가 여러 파티션으로 흩어진다.
- **스윗스팟: 애그리거트 ID를 key로.** 같은 애그리거트의 이벤트는 같은 파티션으로.

### Kafka 순서 보장 심화

**(1) key 라우팅**: `hash(key) % 파티션수`로 같은 key가 같은 파티션으로. key 없으면 round-robin이라 순서 X.

**(2) 프로듀서 함정 — 재시도 순서 역전**: `max.in.flight.requests.per.connection > 1` + 재시도면, 앞 메시지 실패·재전송 사이 뒤 메시지가 먼저 들어갈 수 있음. → `enable.idempotence=true` (시퀀스 번호로 브로커가 정렬 + 중복 제거).

**(3) 파티션 수 변경 함정**: 파티션을 늘리면 `hash(key) % N`의 N이 바뀌어 같은 key가 다른 파티션으로 감. 순서 민감하면 파티션 수를 처음부터 넉넉히 + 고정.

**(4) 컨슈머 함정 — 병렬 처리**: 파티션 내 순서로 받아도, 컨슈머가 멀티스레드/비동기로 처리하면 처리 순서가 깨짐. 파티션 단위 순차 처리 또는 key별 직렬화 큐 필요.

```java
// Spring Kafka — 파티션 순차 처리를 깨지 않는 구조
@KafkaListener(topics = "order.events", groupId = "order-processor")
public void handle(List<ConsumerRecord<String, OrderEvent>> records, Acknowledgment ack) {
    // 같은 파티션의 레코드는 순서대로 들어옴
    // 멀티스레드로 dispatch하면 순서가 깨짐 — 순차 처리
    for (ConsumerRecord<String, OrderEvent> record : records) {
        process(record.value());
    }
    ack.acknowledge();
}
```

**(5) DLQ vs 순서**: 실패 메시지를 DLQ로 보내고 다음으로 진행하면 그 key의 순서가 어긋남. 순서 엄격: "실패 시 그 파티션 멈추고 재시도"(blocking) ↔ 가용성 우선: "DLQ 후 진행".

| 전략 | 순서 | 가용성 | 언제 |
|---|---|---|---|
| blocking retry (그 파티션 멈춤) | 보장 | 낮음 | 순서가 생명 (결제 상태 전이) |
| DLQ 이동 후 진행 | 깨짐 | 높음 | 가용성 우선 (알림, 로그) |

> 핵심: **순서 = 파티션 단위 + 같은 key + `enable.idempotence` + 파티션 수 고정 + 컨슈머 순차 처리.** 한 군데만 놓쳐도 깨진다.

### RabbitMQ에서 순서 보장

RabbitMQ는 파티션 개념이 없다. 순서를 보장하려면:
- **단일 큐 + consumer 1개**: 가장 단순하지만 병렬성 포기. 처리량이 낮아짐.
- **consumer 다수면 순서 깨짐**: round-robin으로 메시지가 분산되므로.
- **prefetch + 재시도 함정**: prefetch가 크면 consumer가 한 번에 많이 가져가서 처리 순서가 꼬임. prefetch=1로 순차 처리.
- **순서가 필요하면 Kafka를 쓰는 게 낫다.** RabbitMQ는 라우팅 유연성·retry/DLQ가 강점이지 순서 보장은 아니다.

### SQS FIFO에서 순서 보장

- **Message Group ID**: 같은 group ID를 가진 메시지가 같은 큐에서 순서대로 처리. group별로 독립.
- **처리량 제한**: FIFO 큐는 표준 큐보다 처리량이 낮음 (300 TPS).
- **순서 + DLQ**: 최대 수신 횟수 초과 시 DLQ로 이동. DLQ처럼 순서가 깨질 수 있음.

### Kafka Streams — 순서 보장을 엔진 차원에서 지원

Kafka Streams는 파티션별 상태 기반 처리를 기본으로 한다:

- **파티션별 상태 store (RocksDB)**: 같은 key의 이벤트가 같은 파티션에서 순차 처리 → 상태 일관성 자연스럽게 보장.
- **`repartition`**: key를 바꿔야 할 때 내부 repartition topic으로 재분배 → 새 key 기준 순서 재정립.
- **aggregate/join**: 시간 윈도우 기반 집계에서 파티션 내 순서가 보장되므로 windowed aggregation이 안전.
- Spring Kafka 수동 consume에서 직접 구현하기 번거로운 "순서 + 상태 + 윈도우" 처리를 Streams DSL (`groupByKey`, `windowedBy`, `aggregate`)로 선언적으로 풀 수 있다.

## 4. consume 성공 후 process 실패 → DLQ

메시지를 받았지만 비즈니스 처리에 실패했을 때, 무한 재시도로 큐가 막히는 걸 방지해야 한다.

### 패턴

```
메시지 수신 → 처리 시도
  ├─ 성공: ack (또는 offset commit)
  ├─ 일시적 실패: 재시도 (backoff + jitter)
  └─ 최대 재시도 초과 또는 복구 불가: DLQ로 이동
```

```mermaid
---
config:
  theme: base
  darkMode: false
---
flowchart LR
  subgraph canvas[" "]
    direction LR
    broker@{ img: "https://cdn.simpleicons.org/apachekafka", label: "MQ / Broker", pos: "b", h: 48, constraint: "on" }
    consume["메시지 수신\n(consume)"]:::app
    process["비즈니스 처리\n(process)"]:::app
    ack["ack / offset commit"]:::ctrl
    retry["재시도\n(backoff + jitter)"]:::ctrl
    check{"성공?"}:::ctrl
    maxCheck{"최대 재시도\n초과?"}:::ctrl
    dlq@{ img: "https://icons.terrastruct.com/aws/Application%20Integration/Amazon-Simple-Queue-Service-SQS_light-bg.svg", label: "DLQ", pos: "b", h: 48, constraint: "on" }

    broker --> consume
    consume --> process
    process --> check
    check --> ack
    check --> maxCheck
    maxCheck --> retry
    retry --> process
    maxCheck --> dlq
  end

  classDef icon fill:transparent,stroke:transparent,stroke-width:0px,color:#111827
  classDef app fill:#EFF6FF,stroke:#3B5BA5,stroke-width:1px,color:#16213E
  classDef ctrl fill:#FFF7ED,stroke:#C98A2B,stroke-width:1px,color:#7A4E0A
  class broker,dlq icon
  style canvas fill:#ffffff,stroke:#ffffff,stroke-width:0px,color:#111827
```

### 구현체별 DLQ

| | RabbitMQ | SQS | Kafka |
|---|---|---|---|
| DLQ 방식 | DLX (Dead Letter Exchange) | DLQ 큐 연결 | DLQ 직접 구성 |
| 자동 여부 | 정책(x-args)으로 자동 이동 | maxReceiveCount 초과 시 자동 | Spring Kafka `DeadLetterPublishingRecoverer` |
| 메시지 손실 | DLQ에 보존 | DLQ에 보존 | DLQ에 보존 (retention 유지) |

### Spring Kafka DLQ 예시

```java
// DefaultErrorHandler로 재시도 + DLQ 이동
DefaultErrorHandler handler = new DefaultErrorHandler(
    new DeadLetterPublishingRecoverer(kafkaTemplate),
    new ExponentialBackOffWithMaxRetries(3)
);
handler.addNotRetryableExceptions(DeserializationException.class); // 복구 불가는 바로 DLQ

factory.getCommonErrorHandler(); // ConcurrentKafkaListenerContainerFactory에 설정
```

### DLQ 설계 원칙

- **DLQ도 토픽이다**: Kafka DLQ는 일반 토픽이므로 컨슈머가 붙을 수 있다. alert·수동 재처리·분석 용도.
- **원본 메시지 보존**: DLQ 메시지에 원본 topic, partition, offset, 실패 원인을 헤더/메타데이터로 포함.
- **재처리 경로**: DLQ → 원본 큐로 재전송하는 도구나 CLI를 둔다. 단, 재처리 시 멱등성이 보장되어야 함.
- **DLQ 적체 모니터링**: DLQ에 메시지가 쌓이면 알림. "DLQ는 임시가 아니라 영구 장애 신호"일 수 있음.

### 순서 vs DLQ 트레이드오프

| 전략 | 순서 | 가용성 | 언제 |
|---|---|---|---|
| blocking retry (그 파티션 멈춤) | 보장 | 낮음 | 순서가 생명 (결제 상태 전이) |
| DLQ 이동 후 진행 | 깨짐 | 높음 | 가용성 우선 (알림, 로그) |

도메인에 따라 선택. 순서가 생명이면 blocking, 가용성이 우선이면 DLQ.

## 5. produce 실패에 대한 대응

프로듀서 실패는 "메시지가 브로커에 안 갔을 때"의 문제다.

### 실패 종류

| 실패 | 원인 | 대응 |
|---|---|---|
| 직렬화 실패 | payload 포맷 오류 | 재시도 무의미 → DLQ 또는 drop + alert |
| connection 실패 | 브로커 다운, 네트워크 | 재시도 + circuit breaker |
| timeout | 브로커 지연 | 재시도 + backoff |
| quota 초과 (429) | rate limit | `Retry-After` 존중 + backoff |
| leader not available | 파티션 리더 선출 중 | 재시도 (Kafka client가 자동 retry) |

### Kafka producer 설정

```java
Properties props = new Properties();
props.put(ProducerConfig.ACKS_CONFIG, "all");           // leader + ISR 전체 ack (유실 방지)
props.put(ProducerConfig.RETRIES_CONFIG, Integer.MAX_VALUE);
props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true); // 중복 제거
props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5); // idempotent면 순서 보장
props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 120000); // send + retry 총 상한
```

- `acks=all`: leader와 모든 ISR이 기록했을 때만 성공. 유실 최소화.
- `enable.idempotence=true`: 재시도로 인한 중복을 브로커가 제거. 순서도 보장.
- `delivery.timeout.ms`: send() 호출부터 재시도 포함 전체 상한. 이 시간이 지나면 `TimeoutException`.

### RabbitMQ publisher confirms

RabbitMQ는 publisher confirms로 브로커 수신을 확인한다:

```java
template.setConfirmCallback((correlationData, ack, cause) -> {
    if (!ack) {
        // 브로커가 안 받음 — 재시도 또는 보관
        log.error("publish failed: {}", cause);
    }
});
template.setReturnsCallback(returned -> {
    // 라우팅 실패 (큐가 없음 등)
    log.error("message returned: {}", returned.getMessage());
});
```

### SQS

- API 호출 자체가 동기 응답이므로 실패 시 즉시 알 수 있음.
- 실패 시 클라이언트 재시도. DLQ는 컨슈머 측에만 있고, 프로듀서 실패는 애플리케이션에서 처리.

### Outbox와의 관계

프로듀서 실패가 잦거나 중요하면 outbox로 원자성을 확보한다. outbox poller가 발행을 재시도하므로, 프로듀서 "일시적 실패"는 outbox가 흡수한다. 단, outbox 행이 계속 쌓이면(브로커 장기 다운) 모니터링 필요.

## 6. produce/publish ↔ consume/subscribe 차이 (심화)

이 용어는 구현체마다 미묘하게 다르다.

| 행위 | RabbitMQ | Kafka | SQS |
|---|---|---|---|
| 메시지 보내기 | publish (to exchange) | produce (to topic) | send (to queue) |
| 메시지 받기 | consume (from queue) | consume (poll from partition) | receive (poll from queue) |
| 관심 등록 | queue bind to exchange | subscribe (consumer group → topic) | N/A (poll만) |
| 확인 | ack | offset commit | delete (또는 visibility timeout) |

- **publish vs produce**: RabbitMQ에선 "exchange에 publish"라고 하고, Kafka에선 "topic에 produce"라고 한다. 같은 행위지만 라우팅 모델이 다르다 — RabbitMQ는 exchange가 라우팅하고, Kafka는 key가 파티션을 결정한다.
- **consume vs subscribe**: Kafka에선 "consumer group이 topic을 subscribe하고, 파티션에서 poll로 consume"한다. subscribe는 "관심 선언"이고 consume은 "실제로 가져오는 행위". RabbitMQ에선 consumer가 큐에 직접 붙어서 consume한다.
- **ack vs commit**: RabbitMQ는 메시지 단위 ack, Kafka는 offset 단위 commit (배치 단위 가능), SQS는 delete로 완료 표시.

## 7. Fan-out (하나의 메시지를 여러 소비자에게)

### 패턴

```
Producer → [Broker] → Consumer A (알림)
                    → Consumer B (분석)
                    → Consumer C (audit)
```

```mermaid
---
config:
  theme: base
  darkMode: false
---
flowchart LR
  subgraph canvas[" "]
    direction LR
    producer["Producer"]:::app

    subgraph rabbitFan["RabbitMQ (fanout exchange)"]
      direction TB
      rEx@{ img: "https://cdn.simpleicons.org/rabbitmq", label: "Exchange\n(fanout)", pos: "b", h: 48, constraint: "on" }
      rQ1["Queue: 알림"]:::db
      rQ2["Queue: 분석"]:::db
      rQ3["Queue: audit"]:::db
      rC1["Consumer A"]:::app
      rC2["Consumer B"]:::app
      rC3["Consumer C"]:::app
      rEx --> rQ1
      rEx --> rQ2
      rEx --> rQ3
      rQ1 --> rC1
      rQ2 --> rC2
      rQ3 --> rC3
    end

    subgraph kafkaFan["Kafka (consumer group)"]
      direction TB
      kTopic@{ img: "https://cdn.simpleicons.org/apachekafka", label: "Topic", pos: "b", h: 48, constraint: "on" }
      kG1["group A\n(offset 독립)"]:::app
      kG2["group B\n(offset 독립)"]:::app
      kG3["group C\n(offset 독립)"]:::app
      kTopic --> kG1
      kTopic --> kG2
      kTopic --> kG3
    end

    producer --> rEx
    producer --> kTopic
  end

  classDef icon fill:transparent,stroke:transparent,stroke-width:0px,color:#111827
  classDef app fill:#EFF6FF,stroke:#3B5BA5,stroke-width:1px,color:#16213E
  classDef db fill:#F0FDF4,stroke:#3F8E55,stroke-width:1px,color:#14532D
  classDef ctrl fill:#FFF7ED,stroke:#C98A2B,stroke-width:1px,color:#7A4E0A
  class kTopic,rEx icon
  style rabbitFan fill:#F8FAFC,stroke:#CBD5E1,stroke-width:1px,color:#111827
  style kafkaFan fill:#F8FAFC,stroke:#CBD5E1,stroke-width:1px,color:#111827
  style canvas fill:#ffffff,stroke:#ffffff,stroke-width:0px,color:#111827
```

### 구현체별 fan-out

| | RabbitMQ | SNS+SQS | Kafka |
|---|---|---|---|
| 방식 | exchange(fanout/topic) → 여러 큐 | SNS → 여러 SQS 구독 | topic → 여러 consumer group |
| 독립성 | 큐마다 독립 소비 | 큐마다 독립 소비 | consumer group마다 독립 offset |
| 메시지 사본 | 각 큐에 복사 | 각 SQS 큐에 복사 | 복사 없음 (같은 로그를 각자 offset에서 읽음) |
| 지연 | 낮음 | 낮음~중간 | 낮음 |

### Kafka의 fan-out이 다른 점

Kafka는 메시지를 복사하지 않는다. 같은 topic의 같은 partition을 여러 consumer group이 각자 offset에서 읽는다. consumer group마다 독립적인 소비 진행도를 가지므로, 한 그룹이 느려도 다른 그룹에 영향을 주지 않는다.

이게 Kafka에서 "topic per event type" 대신 "topic per aggregate + consumer group으로 분기"가 자연스러운 이유다.

### RabbitMQ의 fanout exchange

```java
// exchange type = fanout: 모든 바인딩된 큐에 메시지 복사
rabbitTemplate.convertAndSend("orders.fanout", "", message);
// 각 큐(alarm, analytics, audit)가 독립적으로 consume
```

### SNS fan-out

SNS topic에 여러 SQS 큐를 subscribe하면, SNS가 각 큐에 메시지를 push. 각 SQS 큐가 독립적으로 소비·재시도·DLQ를 가진다. 단, SNS→HTTP/Lambda 구독은 at-least-once 보장이 SQS 큐 기반보다 약할 수 있으므로 중요한 곳은 SQS 구독이 안전.

## 8. 토픽/큐 설계 전략

| 전략 | 소비 | 순서 | 단점 |
|---|---|---|---|
| 단일 큐/토픽 (모든 타입) | consumer가 event_type 필터 | 글로벌 | 소비 로직 복잡·낭비 |
| 토픽/큐 per 애그리거트, key=id | 관심 애그리거트만 구독 | 애그리거트 내 보장 | (보통 정답) |
| 토픽/큐 per 이벤트타입 | 필터 0 | 한 애그리거트 사건이 흩어져 순서 깨짐 | 토픽 폭발 |

## 한 장 요약

```
0. 발전 단계: 인메모리(유실 위험) → Postgres as Queue(원자성, ~수백 TPS) → 외부 MQ(전용 인프라)
   - Postgres as Queue: FOR UPDATE SKIP LOCKED + partial index + visibility timeout + sweeper
   - Outbox ≠ Postgres as Queue: Outbox는 임시 대기소(→ 외부 MQ relay), Postgres as Queue는 최종 큐
1. 전달 보장: 실무 기본은 at-least-once → 컨슈머 멱등성 필수 (멱등키 + 상태 머신 + DB 제약)
2. 순서: 파티션/큐 단위 + 같은 key + 컨슈머 순차 처리. 글로벌 순서 = 병렬성 포기
3. 결과적 일관성: DB 커밋과 발행의 원자성은 Outbox 패턴으로. 안 쓰면 send 실패 대응 필수
4. DLQ: consume 후 처리 실패 → 재시도 → DLQ 이동. 순서 vs 가용성 트레이드오프
5. produce 실패: acks=all, idempotent producer, delivery timeout. 중요하면 Outbox
6. fan-out: RabbitMQ(exchange), SNS(SQS 구독), Kafka(consumer group). Kafka는 복사 없음
7. 토픽 설계: per-애그리거트 + key=id가 스윗스팟. event_type 헤더로 가벼운 필터
9. Kafka Streams: 파티션별 상태 store로 순서+상태+윈도우 처리를 엔진 차원에서 지원
```

> 면접관이 보려는 것: "메시지가 언제 유실되고, 언제 중복되며, 순서가 언제 깨지는가"를 구현체 차이와 함께 설명할 수 있는지. 그리고 그 해법(Outbox, 멱등 컨슈머, DLQ, idempotent producer)을 실무에서 어떻게 적용하는지.