package dev.hyune.mcp.document

/**
 * 문서 전체 목차
 */
data class DocumentOutline(
    val sourceFile: String,
    val items: List<OutlineItem>
)
