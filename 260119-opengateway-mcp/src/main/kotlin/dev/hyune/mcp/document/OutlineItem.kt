package dev.hyune.mcp.document

/**
 * 문서 목차 아이템
 */
data class OutlineItem(
    val id: String,
    val title: String,
    val level: Int,
    val children: MutableList<OutlineItem> = mutableListOf()
)
