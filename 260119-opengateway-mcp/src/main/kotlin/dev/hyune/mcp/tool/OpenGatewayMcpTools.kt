package dev.hyune.mcp.tool

import dev.hyune.mcp.document.DocumentOutline
import dev.hyune.mcp.document.DocumentStore
import dev.hyune.mcp.search.Bm25SearchService
import dev.hyune.mcp.search.SearchResult
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

/**
 * OpenGateway API 문서 검색을 위한 MCP Tools
 * 
 * Claude Desktop, Cursor 등에서 이 도구들을 호출하여
 * OpenGateway 연동 코드를 정확하게 생성할 수 있습니다.
 */
@Service
class OpenGatewayMcpTools(
    private val searchService: Bm25SearchService,
    private val documentStore: DocumentStore
) {
    
    /**
     * OpenGateway API 문서를 검색합니다.
     * 
     * 사용 예시:
     * - "인증 헤더" → 인증 관련 섹션 반환
     * - "chat completions" → Chat API 관련 섹션 반환
     * - "에러 코드 401" → 에러 처리 섹션 반환
     */
    @Tool(description = """
        OpenGateway API 문서에서 관련 내용을 검색합니다.
        인증, API 엔드포인트, 요청/응답 형식, 에러 코드 등을 찾을 때 사용하세요.
        검색 결과로 관련 문서 섹션과 점수가 반환됩니다.
    """)
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
                SearchResultItem(
                    sectionId = result.chunk.id,
                    title = result.chunk.title,
                    breadcrumb = result.chunk.breadcrumb,
                    score = result.score,
                    matchedTerms = result.matchedTerms,
                    contentPreview = result.chunk.content.take(300) + 
                        if (result.chunk.content.length > 300) "..." else ""
                )
            }
        )
    }
    
    /**
     * 문서 전체 목차를 반환합니다.
     * 문서 구조를 파악하거나 특정 섹션을 찾을 때 유용합니다.
     */
    @Tool(description = """
        OpenGateway API 문서의 전체 목차(outline)를 반환합니다.
        문서 구조를 파악하거나 어떤 API들이 있는지 확인할 때 사용하세요.
    """)
    fun getDocsOutline(): OutlineResponse {
        logger.info { "MCP Tool 호출: getDocsOutline()" }
        
        val outlines = documentStore.getOutlines()
        
        return OutlineResponse(
            documents = outlines.map { outline ->
                DocumentOutlineItem(
                    sourceFile = outline.sourceFile,
                    sections = outline.items.map { item -> 
                        flattenOutlineItem(item, 0) 
                    }.flatten()
                )
            }
        )
    }
    
    /**
     * 특정 섹션의 전체 내용을 반환합니다.
     * searchDocs 결과에서 sectionId를 사용하여 상세 내용을 조회합니다.
     */
    @Tool(description = """
        특정 문서 섹션의 전체 내용을 반환합니다.
        searchDocs 결과의 sectionId를 사용하여 해당 섹션의 상세 내용을 확인할 때 사용하세요.
    """)
    fun getDocumentSection(
        @ToolParam(description = "조회할 섹션 ID (searchDocs 결과의 sectionId)") 
        sectionId: String
    ): SectionResponse {
        logger.info { "MCP Tool 호출: getDocumentSection(sectionId='$sectionId')" }
        
        val chunk = documentStore.getChunkById(sectionId)
        
        return if (chunk != null) {
            SectionResponse(
                found = true,
                sectionId = chunk.id,
                title = chunk.title,
                breadcrumb = chunk.breadcrumb,
                content = chunk.content
            )
        } else {
            SectionResponse(
                found = false,
                sectionId = sectionId,
                title = "",
                breadcrumb = emptyList(),
                content = "섹션을 찾을 수 없습니다. getDocsOutline()으로 사용 가능한 섹션을 확인하세요."
            )
        }
    }
    
    // OutlineItem을 평탄화 (계층 구조 → 리스트)
    private fun flattenOutlineItem(item: dev.hyune.mcp.document.OutlineItem, depth: Int): List<OutlineSectionItem> {
        val result = mutableListOf<OutlineSectionItem>()
        result.add(OutlineSectionItem(
            id = item.id,
            title = item.title,
            level = item.level,
            indent = "  ".repeat(depth)
        ))
        for (child in item.children) {
            result.addAll(flattenOutlineItem(child, depth + 1))
        }
        return result
    }
}
