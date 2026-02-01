package dev.hyune.mcp.tool

import dev.hyune.mcp.document.DocumentStore
import dev.hyune.mcp.search.Bm25SearchService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Service

/**
 * OpenGateway API 문서 검색을 위한 MCP Tools
 */
@Service
class OpenGatewayMcpTools(
    private val searchService: Bm25SearchService,
    private val documentStore: DocumentStore
) {
    private val logger = KotlinLogging.logger {}

    @Tool(description = "OpenGateway API 문서에서 관련 내용을 검색합니다.")
    fun searchDocs(
        @ToolParam(description = "검색 키워드") query: String,
        @ToolParam(description = "최대 결과 수") limit: Int = 5
    ): SearchResponse {
        logger.info { "searchDocs(query='$query', limit=$limit)" }

        val results = searchService.search(query, limit)

        return SearchResponse(
            query = query,
            totalResults = results.size,
            results = results.map { result ->
                val doc = result.document
                val content = doc.text ?: ""
                SearchResponse.Item(
                    sectionId = doc.id,
                    title = doc.metadata["title"]?.toString() ?: "",
                    score = result.score,
                    matchedTerms = result.matchedTerms,
                    contentPreview = content.take(300) + if (content.length > 300) "..." else ""
                )
            }
        )
    }

    @Tool(description = "OpenGateway API 문서의 전체 목차를 반환합니다.")
    fun getDocsOutline(): OutlineResponse {
        logger.info { "getDocsOutline()" }

        val grouped = documentStore.getAllDocuments()
            .groupBy { it.metadata["sourceFile"]?.toString() ?: "unknown" }

        return OutlineResponse(
            documents = grouped.map { (sourceFile, docs) ->
                OutlineResponse.Document(
                    sourceFile = sourceFile,
                    sections = docs.map { doc ->
                        OutlineResponse.Section(
                            id = doc.id,
                            title = doc.metadata["title"]?.toString() ?: "",
                            level = (doc.metadata["level"] as? Int) ?: 2
                        )
                    }
                )
            }
        )
    }

    @Tool(description = "특정 문서 섹션의 전체 내용을 반환합니다.")
    fun getDocumentSection(
        @ToolParam(description = "섹션 ID") sectionId: String
    ): SectionResponse {
        logger.info { "getDocumentSection(sectionId='$sectionId')" }

        val doc = documentStore.getDocumentById(sectionId)

        return if (doc != null) {
            SectionResponse(
                found = true,
                sectionId = doc.id,
                title = doc.metadata["title"]?.toString() ?: "",
                content = doc.text ?: ""
            )
        } else {
            SectionResponse(
                found = false,
                sectionId = sectionId,
                title = "",
                content = "섹션을 찾을 수 없습니다."
            )
        }
    }

    data class SearchResponse(
        val query: String,
        val totalResults: Int,
        val results: List<Item>
    ) {
        data class Item(
            val sectionId: String,
            val title: String,
            val score: Double,
            val matchedTerms: List<String>,
            val contentPreview: String
        )
    }

    data class OutlineResponse(
        val documents: List<Document>
    ) {
        data class Document(
            val sourceFile: String,
            val sections: List<Section>
        )

        data class Section(
            val id: String,
            val title: String,
            val level: Int
        )
    }

    data class SectionResponse(
        val found: Boolean,
        val sectionId: String,
        val title: String,
        val content: String
    )
}
