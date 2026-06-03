# 학습 백로그 — 다음에 파볼 주제

> 나중에 질문/정리할 주제를 미리 적어두는 **살아있는 목록**. 답을 채우면 별도 문서로 분리하고 여기선 체크.
> 현재 학습 맥락: [Attention](./260603-attention-기초.md) → [KV 캐시](./260601-kv-cache-기초.md) → [모델 종류](./260603-llm-모델-종류.md) → 서빙 최적화(작성 중·미공개)

## 큐 (우선순위 순 아님)

- [ ] **추론(inference)이 되는 전체 과정**
  - 요청 한 건이 들어와서 토큰이 나오기까지 end-to-end: 토크나이즈 → 임베딩 → forward pass(prefill→decode) → 샘플링 → 디토크나이즈
  - 지금까지 배운 조각들(어텐션·KV 캐시·prefill/decode·샘플링 파라미터)이 한 흐름으로 어떻게 연결되는지 통합
  - "모델이 다음 토큰을 고른다"의 실제 계산 경로 → 관련: [Attention](./260603-attention-기초.md), [KV 캐시](./260601-kv-cache-기초.md), [dictionary 샘플링](./dictionary.md)

- [ ] **그래픽카드별로 서빙되는 수준**
  - GPU 등급(RTX 4090 / L40S / A100 80G / H100 / H200 / B200 / B300)별로 "어떤 모델 크기·컨텍스트 길이·동시 요청"이 현실적으로 가능한가
  - VRAM 예산 = 모델 가중치 + KV 캐시. 양자화(FP16/FP8/AWQ4bit)에 따라 어디까지 올라가나
  - "이 카드면 이 모델"의 실무 매칭표 만들기 → 관련: [모델 종류 §7 VRAM](./260603-llm-모델-종류.md), [KV 캐시 §3](./260601-kv-cache-기초.md)

- [ ] **token per second(TPS)를 늘리는 방법 총정리**
  - 처리량(throughput TPS) vs 지연(TPOT/TTFT) 구분해서 레버 정리: continuous batching, 양자화, speculative decoding, chunked prefill, TP/PP, prefix caching
  - 각 레버가 throughput을 올리나 latency를 줄이나 (둘은 다름!) + 트레이드오프
  - 측정 기준/벤치 방법(어떤 워크로드·동시성에서) → 관련: 서빙 최적화 문서 §2 (작성 중·미공개)

- [ ] **worker 여러 개일 때 cache hit율 검증 방법**
  - prefix/KV cache hit율을 어떻게 "측정"하나 — vLLM Prometheus 메트릭(`gpu_prefix_cache_hit_rate` 등), 라우터 로그
  - round-robin vs cache-aware 라우팅의 hit율을 실제로 비교하는 부하테스트 설계 (같은 prefix 반복 워크로드)
  - 어디서 깨지는지(prefix 1토큰 차이, replica 분산) 관측 → 관련: 서빙 최적화 문서 §3 (작성 중·미공개)

- [ ] **vLLM vs SGLang**
  - 핵심 차이: APC(블록 해싱) vs RadixAttention(토큰 radix tree), 기능/생태계, 라우터
  - 언제 무엇을 고르나 (공유 prefix 많은 멀티턴·에이전트 = SGLang? 범용 = vLLM?)
  - PD disaggregation·양자화·spec decoding 지원 현황 비교 → 관련: 서빙 최적화 문서 §2.1 (작성 중·미공개)

## 완료 (채우면 이쪽으로)

_아직 없음_
