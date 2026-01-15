# My AI Study

백엔드 개발자의 AI 전문가 성장 기록

## 목표

Spring 기반 백엔드 5년차 → **AI 지식이 매우 높은 백엔드 전문가**

## 학습 원칙

- 이론보다 **코드로 이해**
- 논문/수식 ❌ → 실행 가능한 예제 ✅
- Docker 기반 로컬 환경, 필요시 GPU (Runpod)

## 로드맵

| 단계 | 주제 | 핵심 키워드 |
|------|------|-------------|
| 1 | **LLM 기초** | 토큰, 컨텍스트 윈도우, temperature, 시스템 프롬프트, 함수 호출 |
| 2 | **RAG** | 임베딩, 벡터DB, 하이브리드 검색(BM25+벡터), 리랭커, 청킹 |
| 3 | **서빙** | vLLM, TGI, Ollama, 스트리밍(SSE), 캐시, 레이트리밋 |
| 4 | **에이전트** | MCP, 툴 스키마, 멀티툴 오케스트레이션 |
| 5 | **LLM Routing** | 모델 선택, 폴백, 비용/품질 트레이드오프 |
| 6 | **MLOps** | 프롬프트 버전관리, A/B 테스트, 트레이싱, 가드레일 |

## 모듈 목록

| 날짜 | 모듈 | 학습 내용 |
|------|------|-----------|
| 2025-01-15 | [260115-spring-ai-rag-core](./260115-spring-ai-rag-core) | Spring AI + pgvector 기반 RAG 파이프라인, 수동 RAG 구현 |

## 환경

- Java 25, Kotlin 2.3.0
- Spring Boot 4.0.1, Spring AI 2.0.0-M1
- Docker (pgvector, 필요시 Ollama)
- IDE: IntelliJ + Claude Code
