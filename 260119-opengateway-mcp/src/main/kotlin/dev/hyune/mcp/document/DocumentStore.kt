package dev.hyune.mcp.document

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets

private val logger = KotlinLogging.logger {}

/**
 * 문서 저장소
 * - 애플리케이션 시작 시 resources/docs/ 디렉토리의 마크다운 문서 로드
 * - 청킹된 문서와 목차를 메모리에 저장
 */
@Component
class DocumentStore(
    private val chunker: MarkdownChunker
) {
    private val chunks = mutableListOf<DocumentChunk>()
    private val chunkById = mutableMapOf<String, DocumentChunk>()
    private val outlines = mutableListOf<DocumentOutline>()
    
    @PostConstruct
    fun loadDocuments() {
        logger.info { "Loading documents from classpath:docs/" }
        
        val resolver = PathMatchingResourcePatternResolver()
        val resources = resolver.getResources("classpath:docs/*.md")
        
        for (resource in resources) {
            try {
                val filename = resource.filename ?: continue
                val content = resource.inputStream.bufferedReader(StandardCharsets.UTF_8).readText()
                
                // 청킹
                val documentChunks = chunker.chunk(content, filename)
                chunks.addAll(documentChunks)
                
                // ID로 인덱싱 (중복 시 파일명 접미사 추가)
                for (chunk in documentChunks) {
                    val uniqueId = if (chunkById.containsKey(chunk.id)) {
                        "${chunk.id}-${filename.removeSuffix(".md")}"
                    } else {
                        chunk.id
                    }
                    chunkById[uniqueId] = chunk.copy(id = uniqueId)
                }
                
                // 목차 생성
                val outline = chunker.buildOutline(content, filename)
                outlines.add(outline)
                
                logger.info { "Loaded '$filename': ${documentChunks.size} chunks" }
            } catch (e: Exception) {
                logger.error(e) { "Failed to load document: ${resource.filename}" }
            }
        }
        
        logger.info { "Total loaded: ${chunks.size} chunks from ${outlines.size} documents" }
    }
    
    /**
     * 모든 청크 반환
     */
    fun getAllChunks(): List<DocumentChunk> = chunks.toList()
    
    /**
     * ID로 청크 조회
     */
    fun getChunkById(id: String): DocumentChunk? = chunkById[id]
    
    /**
     * 문서 목차 반환
     */
    fun getOutlines(): List<DocumentOutline> = outlines.toList()
    
    /**
     * 특정 문서의 목차 반환
     */
    fun getOutline(sourceFile: String): DocumentOutline? = 
        outlines.find { it.sourceFile == sourceFile }
}
