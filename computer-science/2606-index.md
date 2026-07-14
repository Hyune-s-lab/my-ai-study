# 인덱스(Index)

테이블에서 행을 빨리 찾기 위한 **별도의 정렬된 자료구조**. 책 뒤 색인처럼, 전체를 훑지(Seq Scan) 않고 위치로 점프(Index Scan).

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
    q["쿼리: WHERE filing_id = 35"]
    subgraph idx["B-tree 인덱스 (정렬된 구조)"]
      direction LR
      root["Root\n[ 40 ]"]
      l["< 40\n[10 · 25 · 35]"]
      r["≥ 40\n[40 · 60 · 90]"]
      root --> l
      root --> r
    end
    pg@{ img: "https://icons.terrastruct.com/dev/postgresql.svg", label: "Heap (실제 행 데이터)", pos: "b", h: 48, constraint: "on" }
    q --> root
    l --> pg
  end

  classDef app fill:#EFF6FF,stroke:#3B5BA5,stroke-width:1px,color:#16213E
  classDef ctrl fill:#FFF7ED,stroke:#C98A2B,stroke-width:1px,color:#7A4E0A
  classDef icon fill:transparent,stroke:transparent,stroke-width:0px,color:#111827
  class root,l,r app
  class q ctrl
  class pg icon
  style canvas fill:#ffffff,stroke:#ffffff,stroke-width:0px,color:#111827
  style idx fill:#FBFCFE,stroke:#3B5BA5,stroke-width:1px
```

## 자료구조별 (PostgreSQL)

| 종류 | 잘하는 것 | 예시 |
|---|---|---|
| **B-tree** (기본) | 등치(`=`)·범위(`<,>,BETWEEN`)·정렬·`ORDER BY` | 대부분의 컬럼·PK·UNIQUE |
| **Hash** | 등치(`=`)만 | 범위 안 됨 → 거의 B-tree로 충분 |
| **GIN** | 다중 값 내부 검색 | `jsonb`·배열·전문검색 (`metadata @> …`) |
| **GiST** | 공간·범위·근접 | 위치(PostGIS)·범위 타입 |
| **BRIN** | 거대 + 물리 정렬된 테이블 | 시계열·로그 (작고 가벼움) |

## 인덱스 종류 (개념)

| 종류 | 설명 | 언제 / 예시 |
|---|---|---|
| **단일(single)** | 한 컬럼 | 단일 조건 조회 |
| **복합(composite)** | 여러 컬럼 묶음. **최좌측 접두**만 활용 | `(user_id, created_at)` 동시 조건 |
| **UNIQUE** | 유일성 제약 + 인덱스 | 멱등키 컬럼 |
| **부분(partial)** | 조건(`WHERE`) 맞는 행만 인덱싱 → 작고 빠름 | `… WHERE is_active = true` |
| **표현식(expression)** | 가공값을 인덱싱 | `lower(email)`, `(amount/100)` |
| **커버링(covering, `INCLUDE`)** | 쿼리 컬럼을 인덱스에 담아 **Index-Only Scan**(힙 안 감) | 자주 같이 읽는 컬럼 |

## 인덱스 설계

### 복합 인덱스 — 컬럼 순서가 핵심
- **등치(`=`) 컬럼 먼저, 범위(`<,>`) 컬럼 나중.** (범위 뒤 컬럼은 인덱스 못 탐)
- `(user_id, created_at)` 인덱스는 `user_id=?` 나 `user_id=? AND created_at=?` 엔 타지만, **`created_at` 단독**엔 못 탐(최좌측 깨짐).
- 선택도(selectivity) 높은(값 다양) 컬럼이 앞에 오면 유리.

### 트레이드오프 (공짜 아님)
- **읽기 빨라짐 ↔ 쓰기 느려짐**: INSERT/UPDATE/DELETE마다 인덱스도 갱신. + **저장공간**.
- 인덱스 **남발 금지**: 안 쓰는 인덱스는 쓰기·공간만 갉아먹음. 쿼리 패턴 보고 필요한 것만.

### 인덱스 못 타는 함정
- 컬럼에 **함수/형변환**: `WHERE lower(email)=…`, 타입 불일치 → 표현식 인덱스 필요.
- **선두 와일드카드**: `LIKE '%kim'` 못 탐(`'kim%'` 는 됨).
- **낮은 선택도**: boolean·소수 값 컬럼은 인덱스 효과 적음.
- 복합 인덱스 **최좌측 컬럼 생략**.

## 물리 구조

### Clustered vs Non-clustered
- **MySQL InnoDB**: PK = **clustered index** → 행이 **PK 순서로 물리 저장**. 그래서 랜덤 PK(UUIDv4)면 삽입 위치가 흩어져 페이지 분할↑. 2차 인덱스는 PK를 담아 재조회.
- **PostgreSQL**: 행은 **heap**에 저장(클러스터드 아님). 모든 인덱스가 heap의 `ctid`를 가리킴. `CLUSTER`는 일회성 정렬.
- → 그래서 **UUIDv7**(시간정렬)이 인덱스 지역성에 유리(특히 MySQL).

### 확인: `EXPLAIN`
- `EXPLAIN (ANALYZE, BUFFERS) SELECT …` 로 실제 계획 확인.
- **Seq Scan**(전체 훑음) → **Index Scan**(인덱스 타고 힙) → **Index Only Scan**(힙 안 감, 커버링) → **Bitmap Scan**(여러 인덱스 조합).
- "인덱스 만들었는데 왜 Seq Scan?" → 위 함정 / 통계 / 작은 테이블(옵티마이저가 풀스캔이 빠르다 판단) 점검.

## 실무 시나리오

### 페이지네이션: OFFSET vs Keyset Pagination

`OFFSET` 기반 페이지네이션은 페이지가 깊어질수록 느려진다. OFFSET 10000은 "처음 10000개를 읽고 버린다"는 의미 — 인덱스가 있어도 비용이 선형 증가.

```sql
-- ❌ OFFSET 기반: 페이지 뒤로 갈수록 느림
SELECT * FROM orders
WHERE user_id = 42
ORDER BY created_at DESC
LIMIT 20 OFFSET 10000;

-- ✅ Keyset (cursor) pagination: 어느 페이지든 일정한 속도
SELECT * FROM orders
WHERE user_id = 42
  AND (created_at, id) < ('2026-07-15 10:00:00', 12345)
ORDER BY created_at DESC, id DESC
LIMIT 20;
```

**왜 keyset이 빠른가?** OFFSET은 앞의 N개를 읽고 버리지만, keyset은 시작 지점을 인덱스로 바로 찾는다. `(user_id, created_at DESC, id DESC)` 복합 인덱스가 있으면 완벽히 탄다.

| 구분 | OFFSET | Keyset |
|---|---|---|
| 페이지 깊이 비용 | O(offset + limit) | O(limit) |
| "마지막 페이지" 속도 | 매우 느림 | 동일 |
| 임의 페이지 접근 (3페이지로 점프) | 가능 | 불가 (순차 탐색 필요) |
| 데이터 중간 삽입 시 | 페이지 밀림 현상 | 안정적 |

> **실무 팁**: 관리자 화면처럼 "3페이지로 바로 가기"가 필요하면 OFFSET을, 일반 사용자 무한 스크롤이면 keyset을 써라. page가 10을 넘어가면 OFFSET은 이미 병목이다.

### N+1 문제와 인덱스

N+1은 단순히 ORM의 게으른 로딩(lazy loading) 문제가 아니다. N번의 추가 쿼리가 각각 인덱스를 탄다 해도, **쿼리 자체의 오버헤드(네트워크 왕복, 파싱, 플래닝)**가 누적된다.

```java
// ❌ N+1: user마다 address를 별도 조회
List<User> users = userRepository.findAll();  // 1번
for (User u : users) {
    Address addr = u.getAddress();            // N번 (lazy loading)
}
```

이를 해결하려면 `JOIN`으로 한 번에 가져오거나, `IN` 배치 조회를 쓴다. 단, `IN` 배치 조회 시에도 `(user_id)` 인덱스가 있어야 N건의 인덱스 조회가 빠르다.

> 인덱스로 N+1을 "완화"할 수는 있지만, **근본 해결은 쿼리 수를 줄이는 것**이다. 아무리 인덱스를 타도 1000번의 쿼리는 1번의 JOIN보다 느리다.

### 대량 INSERT 시 인덱스 비용

인덱스가 많을수록 INSERT는 느려진다 — 각 행마다 모든 인덱스 트리를 갱신해야 하므로.

| 인덱스 개수 | 1M 건 INSERT 소요 (예시) | 비고 |
|---|---|---|
| 0개 | ~5초 | 기준선 |
| 3개 | ~20초 | 4배 증가 |
| 10개 | ~60초 | 12배 증가 |

> 수치는 환경에 따라 다름. 핵심은 **인덱스 개수에 비례하여 INSERT 비용이 선형 증가**한다는 점.

**대량 적재(batch load) 전략:**

1. 인덱스 삭제 → 대량 INSERT → 인덱스 재생성 (한 번에 bulk build)
2. `COPY` 명령 사용 (PostgreSQL): 여러 INSERT보다 훨씬 빠름
3. `synchronous_commit = off` (일시적): 트랜잭션 커밋 대기 시간 단축
4. `maintenance_work_mem` 증가: 인덱스 생성 시 정렬 버퍼 확보

```sql
-- 대량 적재 패턴
DROP INDEX IF EXISTS idx_orders_user_created;
-- COPY 또는 batch INSERT 수행
COPY orders FROM '/tmp/orders.csv' WITH (FORMAT csv, HEADER true);
-- 인덱스 재생성 (기존 데이터 기반 bulk build)
CREATE INDEX idx_orders_user_created ON orders(user_id, created_at);
ANALYZE orders;  -- 통계 갱신 필수
```

> **실무 팁**: `maintenance_work_mem`을 1GB 이상으로 임시 설정하면 인덱스 재생성 속도가 수 배 빨라진다. `SET LOCAL maintenance_work_mem = '1GB';`를 세션에만 적용.

## 인덱스 튜닝 실무

### EXPLAIN ANALYZE 읽는 법

`EXPLAIN`은 예상 계획만 보여주지만, `EXPLAIN ANALYZE`는 **실제 실행**해서 실제 시간과 행 수를 보여준다.

```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT * FROM orders
WHERE user_id = 42 AND created_at >= '2026-07-01'
ORDER BY created_at DESC
LIMIT 20;
```

```
Limit  (cost=0.42..12.35 rows=20 width=128) (actual time=0.045..0.082 rows=20 loops=1)
  Buffers: shared hit=15
  ->  Index Scan using idx_orders_user_created on orders  (cost=0.42..45.20 rows=85 width=128) (actual time=0.043..0.078 rows=20 loops=1)
        Index Cond: ((user_id = 42) AND (created_at >= '2026-07-01'::timestamp))
        Buffers: shared hit=15
Planning Time: 0.312 ms
Execution Time: 0.115 ms
```

**읽는 순서:**

| 항목 | 의미 | 점검 포인트 |
|---|---|---|
| `cost=0.42..12.35` | 시작 비용..총 비용 | 첫 값이 높으면 초기 탐색 비용 |
| `rows=85` (예상) vs `actual rows=20` | 예상 행 수 vs 실제 행 수 | 차이가 크면 통계(stale) 갱신 필요 |
| `actual time=0.045..0.082` | 시작..완료 시간 (ms) | 상위 노드 vs 하위 노드 비교 |
| `Buffers: shared hit=15` | 캐시 적중 | `read`(디스크)가 많으면 캐시 미스 |
| `Planning Time` | 플래닝 소요 | 계획 수립 비용 (쿼리 재사용 시 0에 수렴) |
| `Execution Time` | 실제 실행 시간 | 튜닝 전후 비교 기준 |

**함정: 예상 rows와 실제 rows 차이**

옵티마이저가 통계를 기반으로 행 수를 추정하는데, 통계가 오래되면 잘못된 계획을 선택한다. `ANALYZE 테이블명`으로 통계를 갱신하면 해결.

```sql
-- 통계 갱신
ANALYZE orders;

-- 더 상세한 통계 수집
VACUUM ANALYZE orders;
```

### Slow Query Log → 인덱스 후보 식별

**PostgreSQL**: `log_min_duration_statement` 설정으로 느린 쿼리 기록.

```ini
# postgresql.conf
log_min_duration_statement = 100      # 100ms 이상 쿼리 기록
log_line_prefix = '%t [%p] %u@%d '   # 시간, PID, 사용자, DB
```

```sql
-- 또는 현재 실행 중인 쿼리 확인
SELECT pid, now() - pg_stat_activity.query_start AS duration, query
FROM pg_stat_activity
WHERE state = 'active'
ORDER BY duration DESC;
```

**인덱스 후보 식별 흐름:**

1. slow query log에서 반복되는 패턴 추출
2. `WHERE` / `JOIN ON` / `ORDER BY` 컬럼 파악
3. `EXPLAIN ANALYZE`로 Seq Scan이 나오는지 확인
4. 복합 인덱스 후보 설계 (등치 → 범위 → 정렬 순)
5. 인덱스 생성 후 `EXPLAIN ANALYZE`로 비교

> **실무 팁**: 운영에 인덱스를 바로 생성하면 쓰기 락(write lock)이 발생할 수 있다. PostgreSQL은 `CREATE INDEX CONCURRENTLY`를 쓰면 락 없이 생성 가능 (단, 실패 시 `INVALID` 상태로 남으니 주의).

```sql
-- 운영 중 안전하게 인덱스 생성
CREATE INDEX CONCURRENTLY idx_orders_user_created
  ON orders(user_id, created_at);

-- INVALID 인덱스 확인
SELECT indexrelid::regclass AS index_name
FROM pg_index WHERE NOT indisvalid;
```

### 사용되지 않는 인덱스 찾기

인덱스가 쓰기 비용만 발생시키고 읽기에 안 쓰이면 제거 대상이다.

```sql
-- PostgreSQL: 인덱스별 사용 통계
SELECT
    schemaname, relname AS table_name,
    indexrelname AS index_name,
    idx_scan AS scan_count,     -- 스캔 횟수
    idx_tup_read,               -- 읽은 튜플 수
    idx_tup_fetch               -- 실제 페치한 튜플 수
FROM pg_stat_user_indexes
ORDER BY idx_scan ASC;
```

| 상황 | 판단 |
|---|---|
| `idx_scan = 0` + 오래됨 | 삭제 후보 (단, unique 제약용은 제외) |
| `idx_scan` 낮음 + 쓰기 많음 | 쓰기 비용이 읽기 이익을 초과 → 삭제 검토 |
| unique / PK 인덱스 | 스캔 0이어도 삭제 금지 (제약용) |

> **함정**: `pg_stat_user_indexes`는 통계가 리셋될 수 있다 (서버 재시작, `pg_stat_reset()`). 운영 시점에 `idx_scan=0`이면 충분한 기간(최소 1주일) 관찰 후 삭제 여부 결정.

## PostgreSQL 특화 팁

### pg_trgm — 유사도 검색

`LIKE '%keyword%'`는 B-tree 인덱스를 못 탄다(선두 와일드카드). `pg_trgm` 확장을 쓰면 trigram(3글자 단위) 기반 유사도 검색이 가능하고, GIN/GiST 인덱스를 탄다.

```sql
CREATE EXTENSION pg_trgm;

-- trigram GIN 인덱스
CREATE INDEX idx_products_name_trgm ON products USING gin (name gin_trgm_ops);

-- 이제 %keyword% 검색이 인덱스를 탄다
SELECT * FROM products WHERE name ILIKE '%아메리카노%';

-- 유사도 기반 검색 (오타 허용)
SELECT *, similarity(name, '아메리카노') AS sim
FROM products
WHERE name % '아메리카노'
ORDER BY sim DESC
LIMIT 10;
```

| 방식 | 인덱스 | 한계 |
|---|---|---|
| `LIKE '%keyword%'` | B-tree 못 탐 | 전체 스캔 |
| `LIKE 'keyword%'` | B-tree 가능 | 선두 고정만 |
| `pg_trgm` + `ILIKE '%keyword%'` | GIN/GiST | 한국어는 형태소 분리 안 됨 |
| 전문검색 (`tsvector`) | GIN | 형태소 분석 되지만 설정 복잡 |

> **한계**: `pg_trgm`은 단순 문자열 유사도이므로 한국어 형태소 분석은 안 된다. "아메리카노"와 "아메리카노우유"는 유사하지만, "사과"와 "사과하다"는 다르게 처리되어야 하는데 trigram은 그 구분을 못 한다. 한국어 전문검색이 필요하면 `tsvector` + 형태소 분석기 또는 외부 검색 엔진(Elasticsearch)을 고려.

### 부분 인덱스(Partial Index) 활용

조건부 인덱스는 전체 행이 아닌 조건을 만족하는 행만 인덱싱한다. 크기가 작아지고 유지 비용도 줄어든다.

```sql
-- 활성 주문만 조회가 잦은 경우
CREATE INDEX idx_orders_active_user
  ON orders(user_id, created_at)
  WHERE status = 'ACTIVE';

-- 이 쿼리만 인덱스를 탐
SELECT * FROM orders
WHERE status = 'ACTIVE' AND user_id = 42;

-- 이 쿼리는 인덱스를 못 탐 (조건 불일치)
SELECT * FROM orders
WHERE status = 'COMPLETED' AND user_id = 42;
```

**부분 인덱스가 빛나는 시나리오:**

| 시나리오 | 조건 | 효과 |
|---|---|---|
| 소프트 삭제 | `WHERE deleted_at IS NULL` | 삭제된 행 제외 → 인덱스 크기 절반 이하 |
| 활성 사용자만 | `WHERE is_active = true` | 90%가 활성이면 미미, 10%만 활성이면 큰 효과 |
| 특정 상태만 | `WHERE status = 'PENDING'` | 대기 건만 인덱싱 |

> **함정**: 부분 인덱스의 `WHERE` 조건과 쿼리의 `WHERE` 조건이 **정확히 일치**해야 한다. `WHERE status = 'ACTIVE'` 인덱스에 `WHERE status IN ('ACTIVE', 'PENDING')` 쿼리는 인덱스를 못 탄다. 옵티마이저가 조건 호환성을 판단해야 하는데, 복잡한 조건은 매칭이 안 된다.

### REINDEX 시나리오

시간이 지나면 인덱스 페이지가 단편화(fragmentation)되고, 삭제된 행의 공간이 회수되지 않아(bloat) 인덱스가 비대해진다.

```sql
-- 단일 인덱스 재구축
REINDEX INDEX idx_orders_user_created;

-- 테이블 전체 인덱스 재구축
REINDEX TABLE orders;

-- 운영 중 안전하게 (PostgreSQL 12+, 락 최소화)
REINDEX INDEX CONCURRENTLY idx_orders_user_created;
```

**언제 REINDEX가 필요한가?**

| 신호 | 확인 방법 |
|---|---|
| 인덱스 크기가 비정상적으로 큼 | `pg_relation_size('idx_...')` |
| Index Scan 속도 저하 | `EXPLAIN (ANALYZE, BUFFERS)`에서 `Buffers: read` 증가 |
| 대량 DELETE/UPDATE 이후 | 삭제 공간이 회수되지 않음 |

> **실무 팁**: `REINDEX CONCURRENTLY`는 PostgreSQL 12+에서 지원. 단, 실패하면 `INVALID` 인덱스가 남으므로, 실패 후에는 `DROP INDEX` 후 재생성해야 한다. 운영 중에는 `pg_repack` 확장을 쓰면 락 없이 테이블과 인덱스를 모두 재구축 가능.

### Autovacuum과 인덱스 Bloat

PostgreSQL은 MVCC 구조상 UPDATE/DELETE가 실제로는 새 행을 삽입하고 옛 행을 "dead tuple"로 표시한다. `VACUUM`(또는 autovacuum)이 이 dead tuple을 회수하지만, 인덱스 페이지 내부의 빈 공간은 즉시 회수되지 않을 수 있다.

```sql
-- Bloat 추정 (pgstattuple 확장)
CREATE EXTENSION pgstattuple;
SELECT * FROM pgstattuple('orders');
-- avg_leaf_density가 낮으면 (예: 50% 이하) bloat 의심

SELECT * FROM pgstatindex('idx_orders_user_created');
-- avg_leaf_density < 50% → REINDEX 후보
```

**autovacuum 튜닝 포인트:**

```ini
# postgresql.conf
autovacuum = on                          # 기본 on
autovacuum_vacuum_threshold = 50         # dead tuple 50개 이상부터 vacuum
autovacuum_vacuum_scale_factor = 0.2     # 전체 행의 20% 이상 dead → vacuum
autovacuum_naptime = 1min                # vacuum 체크 주기
```

> **함정**: 대량 UPDATE 직후 autovacuum이 바로 실행되지 않으면 dead tuple이 쌓여 쿼리 성능이 급락한다. 배치 작업 후에는 수동 `VACUUM ANALYZE`를 실행하는 것이 안전.

## Spring/JPA 관점

### `@Indexed` 어노테이션

Hibernate는 `@Table(indexes = ...)`로 인덱스를 선언하면 DDL 자동 생성 시 반영한다. 단, `ddl-auto`가 `create` 또는 `update`일 때만.

```java
@Entity
@Table(
    name = "orders",
    indexes = {
        @Index(name = "idx_orders_user_created",
               columnList = "user_id,created_at"),
        @Index(name = "idx_orders_status",
               columnList = "status")
    }
)
public class Order {
    // ...
}
```

> **실무 팁**: `columnList`에서 컬럼 순서가 복합 인덱스 순서를 결정한다. `"user_id,created_at"`과 `"created_at,user_id"`는 완전히 다른 인덱스다. 순서에 주의.

**운영 환경에서의 함정:**

| 설정 | 동작 | 위험 |
|---|---|---|
| `ddl-auto=create` | 시작 시 DROP + CREATE | 데이터 전부 날아감 — 운영 절대 금지 |
| `ddl-auto=update` | 없는 인덱스만 추가 | 기존 인덱스 변경사항 반영 안 됨 |
| `ddl-auto=validate` | 검증만, DDL 생성 안 함 | 운영 권장 — Flyway/Liquibase로 관리 |

> **실무 원칙**: 운영 환경에서는 `ddl-auto=validate` 또는 `none`으로 두고, **Flyway/Liquibase로 인덱스를 버전 관리**해라. Hibernate DDL은 개발 편의용이지 운영 관리 도구가 아니다.

### Hibernate가 생성하는 인덱스

`@ManyToOne` / `@OneToMany` 매핑 시 FK 컬럼이 생성되지만, **Hibernate는 FK 인덱스를 자동으로 만들지 않는다** (DB에 위임). 따라서 FK 컬럼에 인덱스가 없으면 `JOIN` 시 Seq Scan이 발생한다.

```java
@Entity
@Table(name = "order_items",
    indexes = @Index(name = "idx_order_items_order_id",
                     columnList = "order_id"))
public class OrderItem {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;
    // FK는 있지만 인덱스는 직접 선언해야 함
}
```

### `@Query`에서 인덱스 타는/안 타는 패턴

```java
public interface OrderRepository extends JpaRepository<Order, Long> {

    // ✅ 인덱스 탐: (user_id, created_at) 복합 인덱스 활용
    @Query("SELECT o FROM Order o WHERE o.userId = :userId " +
           "AND o.createdAt >= :since ORDER BY o.createdAt DESC")
    List<Order> findByUserSince(@Param("userId") Long userId,
                                 @Param("since") LocalDateTime since);

    // ❌ 인덱스 못 탐: 함수로 가공
    @Query("SELECT o FROM Order o WHERE lower(o.userEmail) = :email")
    List<Order> findByEmailLower(@Param("email") String email);

    // ✅ 표현식 인덱스가 있으면 탐
    // CREATE INDEX idx_orders_lower_email ON orders(lower(user_email))
    @Query("SELECT o FROM Order o WHERE lower(o.userEmail) = :email")
    List<Order> findByEmailWithExprIndex(@Param("email") String email);

    // ❌ 인덱스 못 탐: OR로 서로 다른 컬럼 결합
    @Query("SELECT o FROM Order o WHERE o.userId = :id OR o.status = :status")
    List<Order> findByIdOrStatus(@Param("id") Long id,
                                  @Param("status") String status);

    // ✅ 분리해서 각각 인덱스 탐 (UNION ALL)
    // SELECT ... WHERE user_id = ?
    // UNION ALL
    // SELECT ... WHERE status = ?
}
```

**패턴 요약:**

| 쿼리 패턴 | 인덱스 | 비고 |
|---|---|---|
| `WHERE col = ?` | 단일 인덱스 | 기본 |
| `WHERE col1 = ? AND col2 > ?` | 복합 `(col1, col2)` | 등치 → 범위 순 |
| `WHERE lower(col) = ?` | 표현식 인덱스 필요 | 없으면 Seq Scan |
| `WHERE col1 = ? OR col2 = ?` | 각각 인덱스 + `UNION` | 단일 인덱스로는 안 탐 |
| `WHERE col LIKE 'prefix%'` | B-tree 가능 | 선두 고정 |
| `WHERE col LIKE '%infix%'` | `pg_trgm` GIN 필요 | 없으면 Seq Scan |

### N+1과 `fetch join` / `@EntityGraph`

N+1 문제의 JPA 해결책:

```java
// ❌ N+1 발생
@Entity
public class Order {
    @ManyToOne(fetch = FetchType.LAZY)
    private User user;  // 각 Order마다 user 조회 쿼리 발생
}

List<Order> orders = orderRepository.findAll();
orders.forEach(o -> o.getUser().getName());  // N번 추가 쿼리

// ✅ fetch join: 한 번에 JOIN
public interface OrderRepository extends JpaRepository<Order, Long> {
    @Query("SELECT o FROM Order o JOIN FETCH o.user")
    List<Order> findAllWithUser();
}

// ✅ @EntityGraph: JPQL은 그대로, 페치 대상만 지정
public interface OrderRepository extends JpaRepository<Order, Long> {
    @EntityGraph(attributePaths = {"user", "items"})
    @Query("SELECT o FROM Order o")
    List<Order> findAllWithGraph();
}
```

| 방식 | 장점 | 단점 |
|---|---|---|
| `JOIN FETCH` | 직관적, 한 쿼리 | 컬렉션(`@OneToMany`) 페치 시 `DISTINCT` 필요, 페이징 메모리 위험 |
| `@EntityGraph` | JPQL 재사용, 선언적 | 동일한 제약 (cartesian product 주의) |
| `@BatchSize(size=100)` | lazy 로딩을 `IN` 배치로 | 쿼리는 여러 번, 최적화된 lazy |
| `FETCH` + `LIMIT` 페이징 | 단순 | `OneToMany` 컬렉션 페치 시 메모리 페이징 경고 |

> **함정**: `JOIN FETCH`로 `@OneToMany` 컬렉션을 페치하면 데이터가 뻥튀기(cartesian product)된다. `Order` 1건에 `OrderItem` 10건이면 10행이 반환된다. `DISTINCT`로 중복을 제거해야 하지만, 페이징(`LIMIT`)은 메모리에서 처리되어 대량 데이터에서 OOM이 발생할 수 있다.
>
> → 컬렉션 페치가 필요한 조회는 페이징을 분리하거나, `@BatchSize`로 lazy 로딩을 배치 처리하는 것이 안전하다.

```java
// ✅ 컬렉션 + 페이징: 분리 전략
// 1단계: ID만 페이징
Page<Long> orderIds = orderRepository.findIdsByUserId(userId, pageable);
// 2단계: ID로 페치 조인
List<Order> orders = orderRepository.findByIdInWithItems(orderIds.getContent());

@Query("SELECT o.id FROM Order o WHERE o.userId = :userId")
Page<Long> findIdsByUserId(@Param("userId") Long userId, Pageable pageable);

@Query("SELECT o FROM Order o LEFT JOIN FETCH o.items WHERE o.id IN :ids")
List<Order> findByIdInWithItems(@Param("ids") List<Long> ids);
```

이 패턴에서 `findByIdInWithItems`는 `IN` 절을 쓰므로 `order_id`에 인덱스가 있어야 빠르다. 앞서 언급한 `idx_order_items_order_id` 인덱스가 이를 지원한다.
