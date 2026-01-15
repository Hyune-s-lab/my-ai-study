package dev.hyune.rag.dto

/**
 * RAG 질의응답 결과
 */
data class AskResult(
    val question: String,
    val answer: String,
    val searchResults: List<SearchResult>,
    val llmCalled: Boolean,  // LLM 호출 여부 (검색 결과 없으면 false)
)
