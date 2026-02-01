package dev.hyune.mcp.document

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import org.springframework.ai.document.Document
import org.springframework.ai.reader.markdown.MarkdownDocumentReader
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.stereotype.Component

/**
 * 문서 저장소
 * - Spring AI Document 타입 직접 사용
 * - MarkdownDocumentReader로 청킹
 */
@Component
class DocumentStore {
    private val logger = KotlinLogging.logger {}

    private val documents = mutableListOf<Document>()
    private val documentById = mutableMapOf<String, Document>()

    @PostConstruct
    fun loadDocuments() {
        logger.info { "Loading documents from classpath:docs/" }

        val resolver = PathMatchingResourcePatternResolver()
        val resources = resolver.getResources("classpath:docs/*.md")

        for (resource in resources) {
            try {
                val filename = resource.filename ?: continue

                val reader = MarkdownDocumentReader(
                    resource,
                    MarkdownDocumentReaderConfig.builder()
                        .withHorizontalRuleCreateDocument(true)
                        .withIncludeCodeBlock(true)
                        .withIncludeBlockquote(true)
                        .build()
                )

                val docs = reader.get().mapIndexed { index, doc ->
                    val title = doc.metadata["title"]?.toString() ?: "Section ${index + 1}"
                    val id = generateId(title, index)

                    Document.builder()
                        .id(id)
                        .text(doc.text ?: "")
                        .metadata(doc.metadata + mapOf("id" to id, "sourceFile" to filename))
                        .build()
                }

                documents.addAll(docs)
                docs.forEach { documentById[it.id] = it }

                logger.info { "Loaded '$filename': ${docs.size} documents" }
            } catch (e: Exception) {
                logger.error(e) { "Failed to load: ${resource.filename}" }
            }
        }

        logger.info { "Total: ${documents.size} documents" }
    }

    private fun generateId(title: String, index: Int): String {
        return title.lowercase()
            .replace(Regex("[^a-z0-9가-힣\\s-]"), "")
            .replace(Regex("\\s+"), "-")
            .replace(Regex("-+"), "-")
            .trim('-')
            .ifEmpty { "section-$index" }
    }

    fun getAllDocuments(): List<Document> = documents.toList()

    fun getDocumentById(id: String): Document? = documentById[id]
}
