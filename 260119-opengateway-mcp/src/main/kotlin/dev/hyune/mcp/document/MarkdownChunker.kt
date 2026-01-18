package dev.hyune.mcp.document

import io.github.oshai.kotlinlogging.KotlinLogging
import org.commonmark.node.*
import org.commonmark.parser.Parser
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

/**
 * 마크다운 문서를 헤더 기반으로 청킹하는 컴포넌트
 * 
 * ## 청킹 전략
 * - 레벨 2(##), 레벨 3(###) 헤더를 기준으로 분할
 * - 각 청크는 헤더부터 다음 동급/상위 헤더 전까지의 내용 포함
 * - 계층 구조(breadcrumb) 유지로 컨텍스트 보존
 */
@Component
class MarkdownChunker {
    
    private val parser: Parser = Parser.builder().build()
    
    /**
     * 마크다운 텍스트를 청크 리스트로 변환
     */
    fun chunk(markdown: String, sourceFile: String = ""): List<DocumentChunk> {
        val lines = markdown.lines()
        val chunks = mutableListOf<DocumentChunk>()
        
        // 헤더 위치 찾기
        val headerPositions = findHeaders(lines)
        
        if (headerPositions.isEmpty()) {
            // 헤더가 없으면 전체를 하나의 청크로
            return listOf(
                DocumentChunk(
                    id = generateId(sourceFile.ifEmpty { "document" }),
                    title = sourceFile.ifEmpty { "Document" },
                    level = 1,
                    content = markdown,
                    sourceFile = sourceFile
                )
            )
        }
        
        // 각 헤더 구간을 청크로 변환
        val breadcrumbStack = mutableListOf<Pair<Int, String>>() // (level, title)
        
        for (i in headerPositions.indices) {
            val (lineIndex, level, title) = headerPositions[i]
            val endLineIndex = if (i < headerPositions.size - 1) {
                headerPositions[i + 1].first
            } else {
                lines.size
            }
            
            // breadcrumb 업데이트
            while (breadcrumbStack.isNotEmpty() && breadcrumbStack.last().first >= level) {
                breadcrumbStack.removeLast()
            }
            val breadcrumb = breadcrumbStack.map { it.second }
            breadcrumbStack.add(level to title)
            
            // 청크 내용 추출
            val content = lines.subList(lineIndex, endLineIndex).joinToString("\n").trim()
            
            if (content.isNotEmpty()) {
                chunks.add(
                    DocumentChunk(
                        id = generateId(title),
                        title = title,
                        level = level,
                        content = content,
                        breadcrumb = breadcrumb,
                        sourceFile = sourceFile
                    )
                )
            }
        }
        
        logger.info { "Chunked '$sourceFile' into ${chunks.size} chunks" }
        return chunks
    }
    
    /**
     * 문서 목차 생성
     */
    fun buildOutline(markdown: String, sourceFile: String = ""): DocumentOutline {
        val lines = markdown.lines()
        val headers = findHeaders(lines)
        
        val rootItems = mutableListOf<OutlineItem>()
        val stack = mutableListOf<OutlineItem>() // 현재 계층 스택
        
        for ((_, level, title) in headers) {
            val item = OutlineItem(
                id = generateId(title),
                title = title,
                level = level
            )
            
            // 적절한 부모 찾기
            while (stack.isNotEmpty() && stack.last().level >= level) {
                stack.removeLast()
            }
            
            if (stack.isEmpty()) {
                rootItems.add(item)
            } else {
                stack.last().children.add(item)
            }
            stack.add(item)
        }
        
        return DocumentOutline(sourceFile, rootItems)
    }
    
    /**
     * 마크다운에서 헤더 위치와 정보 추출
     * @return List of (lineIndex, level, title)
     */
    private fun findHeaders(lines: List<String>): List<Triple<Int, Int, String>> {
        val headerRegex = Regex("^(#{1,6})\\s+(.+)$")
        return lines.mapIndexedNotNull { index, line ->
            headerRegex.matchEntire(line.trim())?.let { match ->
                val level = match.groupValues[1].length
                val title = match.groupValues[2].trim()
                Triple(index, level, title)
            }
        }
    }
    
    /**
     * 제목을 kebab-case ID로 변환
     */
    private fun generateId(title: String): String {
        return title
            .lowercase()
            .replace(Regex("[^a-z0-9가-힣\\s-]"), "") // 특수문자 제거 (한글 유지)
            .replace(Regex("\\s+"), "-") // 공백을 하이픈으로
            .replace(Regex("-+"), "-") // 연속 하이픈 제거
            .trim('-')
            .ifEmpty { "section" }
    }
}
