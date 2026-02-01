package dev.hyune.mcp.tool

data class DocumentOutlineItem(
    val sourceFile: String,
    val sections: List<OutlineSectionItem>
)
