package dev.hyune.mcp.tool

data class SectionResponse(
    val found: Boolean,
    val sectionId: String,
    val title: String,
    val content: String
)
