package dev.hyune.mcp.search

import dev.hyune.mcp.document.DocumentChunk

/**
 * BM25 검색 결과
 */
data class SearchResult(
    val chunk: DocumentChunk,
    val score: Double,
    /** 검색어와 매칭된 토큰들 */
    val matchedTerms: List<String>
)
