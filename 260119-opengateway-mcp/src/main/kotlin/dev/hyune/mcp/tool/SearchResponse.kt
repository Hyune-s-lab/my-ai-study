package dev.hyune.mcp.tool

data class SearchResponse(
    val query: String,
    val totalResults: Int,
    val results: List<SearchResultItem>
)
