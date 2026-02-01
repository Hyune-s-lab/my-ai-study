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

    @Tool(
        description = """
        OpenGateway API 문서에서 관련 내용을 검색합니다.
        인증, API 엔드포인트, 요청/응답 형식, 에러 코드 등을 찾을 때 사용하세요.
    """
    )
    fun searchDocs(
        @ToolParam(description = "검색할 키워드 (예: '인증 헤더', 'chat completions', '에러 코드')")
        query: String,
        @ToolParam(description = "반환할 최대 결과 수 (기본값: 5)")
        limit: Int = 5
    ): SearchResponse {
        logger.info { "MCP Tool 호출: searchDocs(query='$query', limit=$limit)" }

        val results = searchService.search(query, limit)

        return SearchResponse(
            query = query,
            totalResults = results.size,
            results = results.map { result ->
                val doc = result.document
                val content = doc.text ?: ""
                SearchResultItem(
                    sectionId = doc.id,
                    title = doc.metadata["title"]?.toString() ?: "",
                    score = result.score,
                    matchedTerms = result.matchedTerms,
                    contentPreview = content.take(300) + if (content.length > 300) "..." else ""
                )
            }
        )
    }

    @Tool(
        description = """
        OpenGateway API 문서의 전체 목차(outline)를 반환합니다.
        문서 구조를 파악하거나 어떤 API들이 있는지 확인할 때 사용하세요.
    """
    )
    fun getDocsOutline(): OutlineResponse {
        logger.info { "MCP Tool 호출: getDocsOutline()" }

        val outlines = documentStore.getOutlines()

        return OutlineResponse(
            documents = outlines.map { outline ->
                DocumentOutlineItem(
                    sourceFile = outline.sourceFile,
                    sections = outline.items.map { item ->
                        OutlineSectionItem(
                            id = item.id,
                            title = item.title,
                            level = item.level
                        )
                    }
                )
            }
        )
    }

    @Tool(
        description = """
        특정 문서 섹션의 전체 내용을 반환합니다.
        searchDocs 결과의 sectionId를 사용하여 해당 섹션의 상세 내용을 확인할 때 사용하세요.
    """
    )
    fun getDocumentSection(
        @ToolParam(description = "조회할 섹션 ID (searchDocs 결과의 sectionId)")
        sectionId: String
    ): SectionResponse {
        logger.info { "MCP Tool 호출: getDocumentSection(sectionId='$sectionId')" }

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
                content = "섹션을 찾을 수 없습니다. getDocsOutline()으로 사용 가능한 섹션을 확인하세요."
            )
        }
    }
}
