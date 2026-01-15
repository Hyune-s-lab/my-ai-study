package dev.hyune.rag.dto

data class HybridSearchResult(
    val content: String,
    val hybridScore: Double,
    val vectorScore: Double,
    val bm25Score: Double,
)
