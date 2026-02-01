package dev.hyune.mcp.search

import org.springframework.ai.document.Document

/**
 * BM25 검색 결과
 */
data class SearchResult(
    val document: Document,
    val score: Double,
    val matchedTerms: List<String>
)
