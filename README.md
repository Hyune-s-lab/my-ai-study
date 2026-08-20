# My AI Study

> 이 레포의 문서들은 Mermaid 11.x 최신 문법(아이콘 노드 등)을 사용하여, GitHub 등 브라우저에서 일부 다이어그램이 렌더링되지 않을 수 있습니다.  
> JetBrains 제품(IntelliJ 등)을 사용 중이라면 [MarkdownNeat](https://plugins.jetbrains.com/plugin/32856-markdownneat) 플러그인 사용을 권장하며, 브라우저에서도 정상 렌더링되도록 Chrome 확장 프로그램 개발을 로드맵에 두고 있습니다.

## Practice

| 프로젝트 | 주제 |
|------|------|
| [spring-ai-rag-core](./spring-ai-rag-core) | Spring AI + pgvector RAG |
| [opengateway-mcp](./opengateway-mcp) | MCP 서버, BM25 검색 |

## [domain-knowledge](./domain-knowledge)

| 문서 | 주제 |
|---|---|
| [dictionary.md](./domain-knowledge/dictionary.md) | 도메인 지식 용어 사전 (OpenAI API·샘플링·서빙) |
| [2605-openai-chat-completions-vs-responses.md](./domain-knowledge/2605-openai-chat-completions-vs-responses.md) | OpenAI Chat Completions vs Responses API |
| [2606-추론모드-용어정리.md](./domain-knowledge/2606-추론모드-용어정리.md) | 추론 모드 프로바이더별 용어 (Thinking/Reasoning) |
| [2606-kv-cache-기초.md](./domain-knowledge/2606-kv-cache-기초.md) | KV 캐시 (기초편) — 개념·그림·메모리 병목 |
| [2606-attention-기초.md](./domain-knowledge/2606-attention-기초.md) | Attention (기초편) — Q/K/V·self-attention·KV 캐시의 뿌리 |
| [2606-llm-모델-종류.md](./domain-knowledge/2606-llm-모델-종류.md) | LLM 모델 종류 — open-weight vs closed·base/instruct·dense/MoE·양자화 |
| [BACKLOG.md](./domain-knowledge/BACKLOG.md) | 다음에 파볼 주제 목록 |

## [computer-science](./computer-science)

| 문서 | 주제 |
|---|---|
| [2606-모의면접-timeout.md](./computer-science/2606-모의면접-timeout.md) | 모의면접 — 타임아웃 (종류·자원고갈·재시도·분산·게이트웨이) |
| [2606-모의면접-redis.md](./computer-science/2606-모의면접-redis.md) | 모의면접 — Redis (싱글스레드·캐시 3대 장애·영속성/HA·분산락·게이트웨이 활용) |
| [2606-kafka-구조.md](./computer-science/2606-kafka-구조.md) | Kafka 구조 (broker·topic·partition·offset·컨슈머 그룹·KRaft·토픽 설계 전략) |
| [2606-sharding-partitioning.md](./computer-science/2606-sharding-partitioning.md) | 샤딩 vs 파티셔닝 (포함관계·복제 구분·샤드키/핫스팟·언제 무엇을) |
| [2606-ddd-aggregate.md](./computer-science/2606-ddd-aggregate.md) | DDD 애그리거트 (일관성 경계·루트·ID 참조·한 트랜잭션 한 애그리거트) |
| [2606-index.md](./computer-science/2606-index.md) | 인덱스 (B-tree/GIN·복합 컬럼 순서·부분/커버링·트레이드오프·clustered vs heap) |
| [2607-race-condition.md](./computer-science/2607-race-condition.md) | race condition (JVM 락·코루틴 Mutex→DB 낙관/비관/네임드 락→데드락·원자적 UPDATE→스케일 아웃→Redisson 분산 락→결과적 일관성) |
| [2607-message-queue.md](./computer-science/2607-message-queue.md) | 메시지 큐 (전달 보장·순서·Outbox·DLQ·fan-out·RabbitMQ/SQS-SNS/Kafka 비교·Redis Streams) |
| [2607-api-key-storage.md](./computer-science/2607-api-key-storage.md) | API Key 안전한 저장과 검증 (bcrypt·salt·pepper·key_id 분리·캐싱) |
| [2607-connection-pool.md](./computer-science/2607-connection-pool.md) | 커넥션 풀 (Tomcat·Netty·HikariCP·가상 스레드·풀 고갈·누수·JDBC 예외·재시도·Bulkhead) |
| [2607-rate-limiting.md](./computer-science/2607-rate-limiting.md) | Rate Limiting (Token Bucket·Leaky Bucket·Sliding Window·백프레셔·Bucket4j·429 응답) |
| [2607-data-structure.md](./computer-science/2607-data-structure.md) | 자료구조 (배열·연결리스트·스택/큐·해시테이블·트리·힙·그래프·시간복잡도) |
| [2607-java-collections.md](./computer-science/2607-java-collections.md) | Java/Kotlin 자료구조 실전 (ArrayList capacity·HashMap 트리 전환·ConcurrentHashMap·BlockingQueue·구현체 선택) |
| [2607-hash.md](./computer-science/2607-hash.md) | 해시 (해시 함수·충돌·equals/hashCode 계약·불변 키·JPA 엔티티 equals/hashCode·캐싱·중복 제거) |
| [2607-network-fundamentals.md](./computer-science/2607-network-fundamentals.md) | 네트워크 기초 (OSI/TCP-IP·TCP vs UDP·3/4-way handshake·HTTP vs HTTPS·TLS) |
| [2608-java-concurrency.md](./computer-science/article-series/2608-java-concurrency.md) | Java Concurrency (JMM/volatile·happens-before·monitor·ReentrantLock/Condition·CAS/LongAdder·ThreadPoolExecutor·CompletableFuture·concurrent collection 계약) |

## [system-design](./system-design)

| 문서 | 주제 |
|---|---|
| [2606-종소세-환급-설계.md](./system-design/2606-종소세-환급-설계.md) | 종소세 환급/연말정산 — 아키텍처(L0→L2 점진 설계) + 도메인 모델·ERD·정합성·증적 |
| [2606-스프링-마이크로서비스-레퍼런스.md](./system-design/2606-스프링-마이크로서비스-레퍼런스.md) | 스프링 클라우드 MSA 레퍼런스 아키텍처 (게이트웨이·Eureka·Resilience4j·Kafka·관측) |
| [2607-spring-멀티모듈-헥사고날-패키징.md](./system-design/2607-spring-멀티모듈-헥사고날-패키징.md) | Spring 멀티모듈 헥사고날 패키징 — Layer Map·포트·어댑터·Spring Modulith·Gradle 멀티모듈·테스트 전략 |
| [enterprise-ai-platform/](./system-design/enterprise-ai-platform/) | AI 설계 4축 — Knowledge Base, Public Model API Gateway, Internal Human AI, Internal Server AI |
