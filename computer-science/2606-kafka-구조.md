# Kafka 구조 정리

분산 **이벤트 로그**다. 메시지를 큐처럼 소비하고 버리는 게 아니라, **append-only 로그**에 쌓아두고(보관기간 동안) 여러 소비자가 각자 위치(offset)에서 읽는다.

## 핵심 용어

| 용어 | 뜻 |
|---|---|
| **Broker** | Kafka 서버 1대. 여러 broker = **Cluster**. 파티션을 나눠 보관·서빙. |
| **Topic** | 메시지 카테고리(이름). 논리적 스트림. 예: `order.events`. |
| **Partition** | 토픽을 쪼갠 단위 = **실제 append-only 로그**. **병렬성 + 순서**의 단위. |
| **Offset** | 파티션 내 메시지의 순번(위치). 컨슈머는 "어디까지 읽었나"를 offset으로 기억. |
| **Producer** | 발행자. 레코드의 **key**로 어느 파티션에 넣을지 결정(`hash(key) % partitions`). |
| **Consumer / Consumer Group** | 구독자. **그룹** 단위로 파티션을 나눠 읽음. |
| **Replication** | 파티션을 여러 broker에 복제(leader 1 + follower N, **ISR**). 내구성·HA. |
| **Record** | key · value · headers · timestamp. |

### 전체 구조 — Topic · Partition · Broker · Consumer

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
    direction LR
    producer@{ img: "https://cdn.simpleicons.org/apachekafka", label: "Producer\nkey → hash(key) % partitions", pos: "b", h: 48, constraint: "on" }

    subgraph topic["Topic: order.events (Kafka Cluster)"]
      direction TB
      p0["Partition 0\nBroker 1 (leader), Broker 2 (follower)\noffset 0,1,2,…"]
      p1["Partition 1\nBroker 2 (leader), Broker 3 (follower)\noffset 0,1,2,…"]
      p2["Partition 2\nBroker 3 (leader), Broker 1 (follower)\noffset 0,1,2,…"]
    end

    subgraph g1["Consumer Group: 알림"]
      direction TB
      c1["Consumer A"]
      c2["Consumer B"]
    end

    subgraph g2["Consumer Group: 회계"]
      c3["Consumer C"]
    end

    producer --> topic
    p0 --> c1
    p1 --> c2
    p2 --> c3
  end

  classDef app fill:#EFF6FF,stroke:#3B5BA5,stroke-width:1px,color:#16213E
  classDef icon fill:transparent,stroke:transparent,stroke-width:0px,color:#111827
  class producer icon
  class p0,p1,p2 app
  class c1,c2,c3 app
  style topic fill:#F8FAFC,stroke:#CBD5E1,stroke-width:1px,color:#111827
  style g1 fill:#ffffff,stroke:#3B5BA5,stroke-width:1px,color:#16213E
  style g2 fill:#ffffff,stroke:#3F8E55,stroke-width:1px,color:#14532D
  style canvas fill:#ffffff,stroke:#ffffff,stroke-width:0px,color:#111827
```

- **Topic** = 논리적 이름. `order.events` 토픽이 3개 파티션으로 구성.
- **Partition** = 실제 로그. 각 파티션이 offset 0,1,2…를 가짐. 같은 key는 같은 파티션 → 순서 보장.
- **Broker 분산**: Partition 0은 Broker 1이 leader, Broker 2가 follower. 복제로 내구성 확보.
- **Consumer Group**: 각 그룹이 독립적으로 파티션을 할당받아 소비.

## 1. 파티션 = 순서와 병렬성의 단위

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
    producer@{ img: "https://cdn.simpleicons.org/apachekafka", label: "Producer\nkey=filing_id → hash(key) % 파티션수\n로 어느 파티션에 넣을지 결정", pos: "b", h: 48, constraint: "on" }

    subgraph topic["Topic: tax.filing (파티션들의 묶음 = 논리적 이름일 뿐)"]
      direction TB
      subgraph p0["Partition 0 — 순서 보장되는 append-only 로그"]
        direction TB
        p0a["offset 0"] --- p0b["1"] --- p0c["2"] --- p0d["3"] --- p0e["다음 append →"]
      end
      subgraph p1["Partition 1"]
        direction TB
        p1a["offset 0"] --- p1b["1"] --- p1c["다음 append →"]
      end
      subgraph p2["Partition 2"]
        direction TB
        p2a["offset 0"] --- p2b["1"] --- p2c["2"] --- p2d["다음 append →"]
      end
    end

    producer --> p0
    producer --> p1
    producer --> p2
  end

  classDef cell fill:#EFF6FF,stroke:#3B5BA5,stroke-width:1px,color:#16213E
  classDef tail fill:#F0FDF4,stroke:#3F8E55,stroke-width:1px,color:#14532D
  classDef icon fill:transparent,stroke:transparent,stroke-width:0px,color:#111827
  class p0a,p0b,p0c,p0d,p1a,p1b,p2a,p2b,p2c cell
  class p0e,p1c,p2d tail
  class producer icon
  style topic fill:#FBFCFE,stroke:#3B5BA5,stroke-width:1px
  style p0 fill:#F8FAFC,stroke:#CBD5E1,stroke-width:1px,color:#111827
  style p1 fill:#F8FAFC,stroke:#CBD5E1,stroke-width:1px,color:#111827
  style p2 fill:#F8FAFC,stroke:#CBD5E1,stroke-width:1px,color:#111827
  style canvas fill:#ffffff,stroke:#ffffff,stroke-width:0px,color:#111827
```

- **Topic = 논리적 이름**(파티션들의 묶음). **Partition = 실제 데이터** = 순서 보장되는 **append-only 로그**(offset 0,1,2…).
- **순서는 "파티션 안에서만" 보장**된다. 토픽 전체 순서는 보장 안 됨.
- 파티션 수 = **최대 병렬 소비자 수**. (한 파티션은 그룹 내 한 컨슈머만 읽음) → **순서(파티션↓) vs 병렬성(파티션↑)** 트레이드오프.

## 2. 순서 보장(ordering) — 어떻게 깨지고 어떻게 지키나

카프카 설계의 핵심. **순서 단위는 파티션**이고, 그걸 지키려면 발행·파티션·소비 3군데를 다 봐야 한다.

**(1) key 라우팅 — 순서 단위 정하기**
- `hash(key) % 파티션수` 로 파티션 결정 → **같은 key = 같은 파티션 = 순서 보장**. key 없으면 라운드로빈이라 순서 X.
- **무엇을 key로?** = "무엇 단위로 순서를 지킬까". 예: `key=order_id` → 한 주문의 사건들(`Created→Paid`)이 순서대로. (서로 다른 주문끼리는 순서 무관)

**(2) 프로듀서 함정 — 재시도 순서 역전**
- `max.in.flight.requests.per.connection > 1` + 재시도면, 앞 메시지가 실패·재전송되는 사이 **뒤 메시지가 먼저** 들어가 순서가 뒤집힐 수 있음.
- **해결: `enable.idempotence=true`** (현대 기본 권장). 시퀀스 번호로 브로커가 **정렬 + 중복 제거** → in-flight 5까지도 순서·정확히 한 번 보장. (옛날엔 `max.in.flight=1`로 낮췄지만 처리량 손해)

**(3) 파티션 수 변경 함정**
- 파티션을 **늘리면** `% N`의 N이 바뀜 → 같은 key가 **다른 파티션**으로 감 → 과거·신규가 흩어져 **순서 보장 깨짐**.
- 순서 민감하면 **파티션 수를 처음부터 넉넉히 + 고정**. (늘릴 거면 순서 끊김 감수)

**(4) 컨슈머 함정 — 병렬 처리**
- 파티션 내 순서로 **받아도**, 컨슈머가 멀티스레드/비동기로 처리하면 **처리 순서가 깨짐**.
- 파티션 단위로 **순차 처리**, 또는 key별 직렬화 큐로 처리 순서 보존.

**(5) DLQ vs 순서 트레이드오프**
- 실패 메시지를 **DLQ로 보내고 다음으로 진행**하면 그 key의 순서가 어긋남.
- 순서 엄격: "실패 시 그 파티션 멈추고 재시도"(blocking) ↔ 가용성 우선: "DLQ 후 진행". 도메인에 맞게 선택.

**(6) 글로벌 순서가 정말 필요하면** → 파티션 1개(병렬성 포기). 보통은 **key 단위 순서로 충분**.

> 요약: **순서 = 파티션 단위 + 같은 key + `enable.idempotence` + 파티션 수 고정 + 컨슈머 순차 처리.** 한 군데만 놓쳐도 깨진다.

## 3. 컨슈머 그룹
- **그룹 안에서 파티션을 나눠** 읽는다 → 파티션당 그룹 내 **컨슈머 1개**. (그림: 알림 그룹의 A가 P0·P1, B가 P2)
- **그룹은 서로 독립** → 같은 토픽을 알림 그룹·회계 그룹이 **각자 전부** 소비(각자 offset). = pub/sub.
- 컨슈머가 죽거나 추가되면 **리밸런싱**(파티션 재분배).
- 컨슈머 < 파티션: 일부 컨슈머가 여러 파티션. 컨슈머 > 파티션: **남는 컨슈머는 논다**(파티션 수가 상한).

## 4. ZooKeeper vs KRaft

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
    subgraph zkmode["① ZooKeeper 모드 (레거시 · ~Kafka 3.x)"]
      direction TB
      z["ZooKeeper 앙상블\n(별도 클러스터 · 메타데이터·선출 보관)"]
      zk_b1@{ img: "https://cdn.simpleicons.org/apachekafka", label: "Broker 1\n(+ Active Controller · 선출됨)", pos: "b", h: 48, constraint: "on" }
      zk_b2@{ img: "https://cdn.simpleicons.org/apachekafka", label: "Broker 2", pos: "b", h: 48, constraint: "on" }
      zk_b3@{ img: "https://cdn.simpleicons.org/apachekafka", label: "Broker 3", pos: "b", h: 48, constraint: "on" }
      zk_b1 --> z
      zk_b2 --> z
      zk_b3 --> z
    end

    subgraph kraftmode["② KRaft 모드 (Kafka 3.3+ · 4.0 전용)"]
      direction TB
      cq@{ img: "https://cdn.simpleicons.org/apachekafka", label: "Controller Quorum\n(__cluster_metadata · 내부 Raft 합의)", pos: "b", h: 48, constraint: "on" }
      k_b1@{ img: "https://cdn.simpleicons.org/apachekafka", label: "Broker 1", pos: "b", h: 48, constraint: "on" }
      k_b2@{ img: "https://cdn.simpleicons.org/apachekafka", label: "Broker 2", pos: "b", h: 48, constraint: "on" }
      k_b3@{ img: "https://cdn.simpleicons.org/apachekafka", label: "Broker 3", pos: "b", h: 48, constraint: "on" }
      k_b1 --> cq
      k_b2 --> cq
      k_b3 --> cq
    end
  end

  classDef broker fill:#EFF6FF,stroke:#3B5BA5,stroke-width:1px,color:#16213E
  classDef zk fill:#FFF7ED,stroke:#C98A2B,stroke-width:1px,color:#7A4E0A
  classDef ctrl fill:#F0FDF4,stroke:#3F8E55,stroke-width:1px,color:#14532D
  classDef icon fill:transparent,stroke:transparent,stroke-width:0px,color:#111827
  class zk_b1,zk_b2,zk_b3,k_b1,k_b2,k_b3,cq icon
  class z zk
  style zkmode fill:#ffffff,stroke:#C98A2B,stroke-width:1px
  style kraftmode fill:#ffffff,stroke:#3F8E55,stroke-width:1px
  style canvas fill:#ffffff,stroke:#ffffff,stroke-width:0px,color:#111827
```

- **① ZooKeeper 모드(레거시)**: 메타데이터·컨트롤러 선출·구성 관리를 **별도 ZooKeeper 앙상블**이 담당. broker 중 1대가 **Active Controller**로 선출됨. → 클러스터 2개(Kafka+ZK) 운영.
- **② KRaft 모드(KIP-500)**: ZooKeeper 제거, **Kafka 자체가 Raft 합의**로 메타데이터 관리(`__cluster_metadata` 로그). **전용 Controller Quorum**이 그 역할.

| 항목 | ZooKeeper 모드 | KRaft 모드 |
|---|---|---|
| 메타데이터 저장 | 외부 ZooKeeper 앙상블 | 내부 Controller(Raft 로그) |
| 운영 | 클러스터 **2개**(Kafka+ZK) | **1개**(Kafka만) |
| 컨트롤러 | broker 중 1개 **선출**(단일) | **전용 quorum** |
| 메타데이터 확장성 | 수만 파티션서 병목 | 로그 기반, 큼 |
| 장애 복구 | 컨트롤러 페일오버 느림 | Raft 로그 재생, 빠름 |
| 버전 | ~3.x 기본 | 3.3+ production, **4.0(2025) 전용** |

→ 신규는 **KRaft 기본**. "주키퍼"는 레거시 얘기.

## 5. 보관(retention) & 로그 컴팩션
- **시간/크기 기반 retention**: 일정 기간/용량까지 보관 후 삭제. (큐처럼 "읽으면 삭제"가 아님)
- **Log Compaction**: key별 **최신 값만** 남기고 과거 제거 → 토픽이 "현재 상태 스냅샷"처럼 동작. (DB의 현재상태 테이블 ↔ 전체 이벤트 로그 관계와 같은 결)

## 6. 전달 보장
- **at-most-once / at-least-once / exactly-once**.
- 기본 실무는 **at-least-once**(중복 가능) → **컨슈머 멱등성 필수**. (멱등키·중복 처리와 직결)
- exactly-once: 멱등 프로듀서 + 트랜잭션(오버헤드 있음).

## 7. 토픽 설계 전략 (consume 복잡도 ↔ 순서 ↔ 토픽 수)

| 전략 | 소비 | 순서 | 단점 |
|---|---|---|---|
| **단일 토픽(모든 타입)** | consumer가 `event_type` 필터 + 관심 없는 것도 다 받음 | 글로벌 | 소비 로직 복잡·낭비 |
| **토픽 per 애그리거트** (`order`·`payment`), key=id | 관심 애그리거트만 구독 + event_type은 **헤더로 가벼운 필터** | **애그리거트 내 보장** | (보통 정답) |
| **토픽 per 이벤트타입** (`order.created`…) | 필터 0 | 한 애그리거트 사건이 흩어져 **순서 깨짐** | 토픽 폭발 |

핵심 트레이드오프: **나눌수록 consumer 필터는 줄지만, (같은 애그리거트 내) 순서 보장과 토픽 수가 나빠진다.** 수명주기 엔티티는 순서가 중요 → **per-애그리거트가 스윗스팟.**

> 쓰기(DB 아웃박스 테이블)와 전송(토픽)은 **독립 결정**. 단일 `domain_event` 테이블로 써놓고 → 발행 때 `aggregate_type`으로 **토픽 라우팅**도 가능.

## 8. 실무 운영 관점

### 컨슈머 lag 모니터링

- `consumer_lag` = 파티션의 log-end offset − consumer group의 committed offset. lag이 커지면 컨슈머가 따라가지 못한다는 뜻.
- Prometheus + Grafana로 lag 메트릭 수집. `kafka_consumer_lag` 알림 설정.
- lag 원인: 처리 속도 < 프로듀스 속도, 컨슈머 장애, 파티션 재분배.

### 파티션 수 결정

- 처리량 기준: `목표 처리량 ÷ 컨슈머 1개당 처리량`을 파티션 수의 하한으로.
- 너무 적으면 병렬성 부족, 너무 많으면 브로커 메타데이터 부담·컨슈머 idle.
- 늘리면 순서가 깨지므로(#2 참조), 처음부터 넉넉히 + 고정.

### ISR (In-Sync Replicas)

- `acks=all`: leader와 모든 ISR이 기록했을 때만 ack. 유실 최소화.
- ISR이 줄어들면 `min.insync.replicas`를 충족 못 해 쓰기가 거부됨. 가용성과 내구성의 트레이드오프.
- 운영 중 ISR 이탈 알림: `UnderReplicatedPartitions` 메트릭.

### 컨슈머 rebalance

- 컨슈머 추가·제거·장애 시 파티션 재분배(rebalance).
- rebalance 중에는 **컨슈머가 일시적으로 메시지를 못 처리** → stop-the-world.
- `partition.assignment.strategy`: `RangeAssignor`(기본), `StickyAssignor`(재할당 최소화), `CooperativeStickyAssignor`(incremental rebalance, stop-the-world 최소화).

### Kafka Streams — 순서 + 상태 + 윈도우 처리

파티션별 상태 store(RocksDB)로 같은 key의 이벤트가 순차 처리되어 상태 일관성이 보장된다. `groupByKey`, `windowedBy`, `aggregate`로 시간 윈도우 집계를 선언적으로 처리할 수 있다. Spring Kafka 수동 consume에서 직접 구현하기 번거로운 부분을 엔진 차원에서 지원.
