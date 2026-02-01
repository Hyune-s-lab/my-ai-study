package dev.hyune.mcp.document

/**
 * 문서 전체 목차
 */
data class DocumentOutline(
    val sourceFile: String,
    val items: List<Item>
) {
    data class Item(
        val id: String,
        val title: String,
        val level: Int
    )
}
