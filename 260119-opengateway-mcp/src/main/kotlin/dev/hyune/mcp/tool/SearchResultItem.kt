package dev.hyune.mcp.tool

data class SearchResultItem(
    val sectionId: String,
    val title: String,
    val breadcrumb: List<String>,
    val score: Double,
    val matchedTerms: List<String>,
    val contentPreview: String
)
