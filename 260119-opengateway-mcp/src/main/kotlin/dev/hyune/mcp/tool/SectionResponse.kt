package dev.hyune.mcp.tool

data class SectionResponse(
    val found: Boolean,
    val sectionId: String,
    val title: String,
    val breadcrumb: List<String>,
    val content: String
)
