package dev.hyune.mcp.document

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import org.springframework.ai.reader.markdown.MarkdownDocumentReader
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.stereotype.Component

/**
 * 문서 저장소
 * - 애플리케이션 시작 시 resources/docs/ 디렉토리의 마크다운 문서 로드
 * - Spring AI MarkdownDocumentReader로 청킹
 */
@Component
class DocumentStore {
    private val logger = KotlinLogging.logger {}

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

                // Spring AI MarkdownDocumentReader 사용
                val reader = MarkdownDocumentReader(
                    resource,
                    MarkdownDocumentReaderConfig.builder()
                        .withHorizontalRuleCreateDocument(true)
                        .withIncludeCodeBlock(true)
                        .withIncludeBlockquote(true)
                        .build()
                )

                val documents = reader.get()

                // Spring AI Document → DocumentChunk 변환
                val documentChunks = documents.mapIndexed { index, doc ->
                    val title = doc.metadata["title"]?.toString() ?: "Section ${index + 1}"
                    val id = generateId(title, index)

                    DocumentChunk(
                        id = id,
                        title = title,
                        level = (doc.metadata["level"] as? Int) ?: 2,
                        content = doc.text ?: "",
                        breadcrumb = emptyList(),
                        sourceFile = filename
                    )
                }

                chunks.addAll(documentChunks)

                // ID로 인덱싱
                for (chunk in documentChunks) {
                    val uniqueId = if (chunkById.containsKey(chunk.id)) {
                        "${chunk.id}-${chunks.indexOf(chunk)}"
                    } else {
                        chunk.id
                    }
                    chunkById[uniqueId] = chunk.copy(id = uniqueId)
                }

                // 간단한 목차 생성
                val outlineItems = documentChunks.map { chunk ->
                    DocumentOutline.Item(
                        id = chunk.id,
                        title = chunk.title,
                        level = chunk.level
                    )
                }
                outlines.add(DocumentOutline(filename, outlineItems))

                logger.info { "Loaded '$filename': ${documentChunks.size} chunks" }
            } catch (e: Exception) {
                logger.error(e) { "Failed to load document: ${resource.filename}" }
            }
        }

        logger.info { "Total loaded: ${chunks.size} chunks from ${outlines.size} documents" }
    }

    private fun generateId(title: String, index: Int): String {
        val baseId = title
            .lowercase()
            .replace(Regex("[^a-z0-9가-힣\\s-]"), "")
            .replace(Regex("\\s+"), "-")
            .replace(Regex("-+"), "-")
            .trim('-')
            .ifEmpty { "section-$index" }
        return baseId
    }

    fun getAllChunks(): List<DocumentChunk> = chunks.toList()

    fun getChunkById(id: String): DocumentChunk? = chunkById[id]

    fun getOutlines(): List<DocumentOutline> = outlines.toList()
}
