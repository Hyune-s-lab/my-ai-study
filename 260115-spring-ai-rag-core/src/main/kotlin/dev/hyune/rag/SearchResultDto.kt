package dev.hyune.rag

/**
 * 검색 결과 DTO
 */
data class SearchResultDto(
    val content: String,
    val score: Double,
)
