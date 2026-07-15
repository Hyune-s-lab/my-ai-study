# 샤딩(Sharding) vs 파티셔닝(Partitioning)

헷갈리는 이유: **샤딩은 파티셔닝의 한 종류**다. "여러 노드에 걸친 수평 파티셔닝"이 샤딩.

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

    subgraph h["① 수평 파티셔닝 (행 분할 · 단일 DB)"]
      direction TB
      h_pg@{ img: "https://icons.terrastruct.com/dev/postgresql.svg", label: "PostgreSQL", pos: "b", h: 48, constraint: "on" }
      h_p1["rows · 2023"]
      h_p2["rows · 2024"]
      h_p3["rows · 2025"]
    end

    subgraph v["② 수직 파티셔닝 (컬럼 분할 · 단일 DB)"]
      direction TB
      v_pg@{ img: "https://icons.terrastruct.com/dev/postgresql.svg", label: "PostgreSQL", pos: "b", h: 48, constraint: "on" }
      v_t1["orders\n(id, status, …) ← hot"]
      v_t2["order_details\n(id, payload) ← cold"]
      v_t1 ---|"같은 PK"| v_t2
    end

    subgraph s["③ 샤딩 (행 분할 · 여러 노드)"]
      direction TB
      s_router["샤드 라우터\n(shard key)"]
      s_n1@{ img: "https://icons.terrastruct.com/dev/postgresql.svg", label: "Node 1 / Shard 1", pos: "b", h: 48, constraint: "on" }
      s_n2@{ img: "https://icons.terrastruct.com/dev/postgresql.svg", label: "Node 2 / Shard 2", pos: "b", h: 48, constraint: "on" }
      s_n3@{ img: "https://icons.terrastruct.com/dev/postgresql.svg", label: "Node 3 / Shard 3", pos: "b", h: 48, constraint: "on" }
      s_router --> s_n1
      s_router --> s_n2
      s_router --> s_n3
    end
  end

  classDef app fill:#EFF6FF,stroke:#3B5BA5,stroke-width:1px,color:#16213E
  classDef ctrl fill:#FFF7ED,stroke:#C98A2B,stroke-width:1px,color:#7A4E0A
  classDef icon fill:transparent,stroke:transparent,stroke-width:0px,color:#111827
  class h_p1,h_p2,h_p3,v_t1,v_t2 app
  class s_router ctrl
  class h_pg,v_pg,s_n1,s_n2,s_n3 icon
  style h fill:#ffffff,stroke:#3B5BA5,stroke-width:1px
  style v fill:#ffffff,stroke:#3B5BA5,stroke-width:1px
  style s fill:#ffffff,stroke:#3F8E55,stroke-width:1px
  style canvas fill:#ffffff,stroke:#ffffff,stroke-width:0px,color:#111827
```

## 한눈 비교 — 수평 파티셔닝 · 수직 파티셔닝 · 샤딩

| 기준 | 수평 파티셔닝 | 수직 파티셔닝 | 샤딩 |
|---|---|---|---|
| 나누는 것 | **행(rows)** | **컬럼(columns)** | **행(rows)** |
| 분산 범위 | 단일 DB | 단일 DB | **여러 노드** |
| 방식 | range / hash / list | hot/cold 컬럼 분리 | 수평 파티셔닝을 노드로 분산 |
| 목적 | 관리·프루닝·보관/파기 | hot 행 슬림(캐시·스캔↑) | **수평 확장**(용량·쓰기) |
| 트랜잭션·조인 | 로컬, 쉬움 | 합치려면 조인 | **크로스샤드 어려움**(분산 트랜잭션) |
| 예시 | 연도별 파티션 | 본문 ↔ 메타데이터 분리 | Vitess·Citus·MongoDB |

- **샤딩 = 여러 노드로 흩은 수평 파티셔닝** (샤딩 ⊂ 수평). **수직은 다른 축**(같은 노드, 컬럼 분리).
- PostgreSQL은 큰 컬럼을 **TOAST**가 자동 out-of-line 저장 → 수직 분리 없이도 대부분 해결.

## 복제(Replication)와 헷갈리지 말 것
- **분할(파티셔닝/샤딩)** = 데이터를 **쪼갬** → 용량·쓰기 확장.
- **복제(replication)** = 데이터를 **복사** → HA·읽기 확장.
- 보통 **같이** 씀: 각 샤드를 다시 복제(leader/replica). (Kafka도 partition을 broker에 분산 + 복제)

## 샤딩이 어려운 이유 (= 최후 수단인 이유)
- **샤드 키**: 잘못 고르면 **핫스팟**(쏠림).
- **크로스 샤드 쿼리**: scatter-gather → 느림·조인 제약.
- **분산 트랜잭션**: 2PC 회피 → **사가**.
- **리샤딩**: 샤드 추가 시 재분배(consistent hashing으로 완화).

## 언제 무엇을
**파티셔닝(관리/성능) → 읽기 복제본+캐시(읽기 확장) → 그래도 단일 머신 쓰기/용량 한계면 샤딩**(복잡도 큼, 최후 수단).

## 실무 관점

### 샤드 키 설계가 전부

샤드 키를 잘못 고르면 모든 것이 깨진다:

| 문제 | 원인 | 결과 |
|---|---|---|
| 핫스팟 | 특정 키에 트래픽 쏠림 (예: `user_id` 범위가 한쪽으로 편중) | 한 샤드만 과부하, 나머지는 idle |
| 데이터 불균형 | 키 분포가 균일하지 않음 | 한 샤드 용량 초과 |
| 크로스 샤드 조인 | 관련 데이터가 여러 샤드에 흩어짐 | scatter-gather, 느림 |

- **해시 샤딩**: `hash(key) % N`으로 균등 분산. 핫스팟 방지. 단, 범위 쿼리가 안 됨.
- **범위 샤딩**: 키 범위별로 분산. 범위 쿼리 가능. 단, 핫스팟 위험.
- **디렉토리 샤딩**: 조회 테이블로 키→샤드 매핑. 유연하지만 디렉토리가 단일 장애점.

### 리샤딩 (Resharding)

샤드 추가 시 데이터를 재분배해야 한다:
- **consistent hashing**: 링 구조에서 샤드 추가 시 인접 샤드의 일부만 이동. 이동량 최소화.
- **더블라이트(double-write)**: 구샤드·신샤드에 동시 쓰기 → 백필 → 컷오버. 다운타임 없지만 복잡.
- **오프라인 마이그레이션**: 서비스 중단 후 일괄 이동. 단순하지만 다운타임.

### Spring/JPA에서 샤딩

Spring에서 샤딩을 적용하면:
- **DataSource 라우팅**: `AbstractRoutingDataSource`로 요청별 샤드 DataSource 선택. `ThreadLocal`에 샤드 키 설정.
- **Shardingsphere (ShardingSphere-JDBC)**: SQL 파싱·라우팅을 JDBC 레이어에서 처리. 애플리케이션은 단일 DataSource처럼 사용.
- **애플리케이션 레벨**: 직접 샤드 선택 로직 구현. 가장 유연하지만 유지보수 부담.

```java
// AbstractRoutingDataSource 예시
public class ShardingDataSource extends AbstractRoutingDataSource {
    @Override
    protected Object determineCurrentLookupKey() {
        return ShardContext.get(); // ThreadLocal에서 샤드 키
    }
}
```

### 샤딩 안 하는게 더 좋은 이유

- 크로스 샤드 트랜잭션: 2PC 회피 → 사가 패턴. 복잡도 폭발.
- 조인 불가: 애플리케이션에서 조합. N+1 위험.
- 운영 복잡: 백업·마이그레이션·스키마 변경이 N배.
- **대안 우선**: 읽기 복제본, 캐시, 파티셔닝, 컬럼 압축으로 먼저 해결. 샤딩은 정말 최후.

## 다른 맥락의 "partition" (용어 혼동 주의)
- **Kafka partition**: 토픽을 쪼갠 단위(브로커 분산) — 사실상 토픽의 샤딩·파티셔닝. (→ [Kafka 구조](./260617-kafka-구조.md))
- **CAP의 partition tolerance**: 네트워크 **분단** 내성 — 위 분할과 전혀 다른 의미.
