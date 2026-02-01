package dev.hyune.rag.dto

/**
 * 검색 결과
 */
data class SearchResult(
    val content: String,
    val score: Double,
    val metadata: Map<String, Any> = emptyMap(),
)
