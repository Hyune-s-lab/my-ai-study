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
 * - 애플리케이션 시작 시 resources/docs/ 디렉토리의 마크다운 문서 로드
 * - Spring AI MarkdownDocumentReader로 청킹
 * - Spring AI Document 타입 직접 사용
 */
@Component
class DocumentStore {
    private val logger = KotlinLogging.logger {}

    private val documents = mutableListOf<Document>()
    private val documentById = mutableMapOf<String, Document>()
    private val outlines = mutableListOf<DocumentOutline>()

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

                val docs = reader.get()

                // ID 생성 및 메타데이터 보강
                val processedDocs = docs.mapIndexed { index, doc ->
                    val title = doc.metadata["title"]?.toString() ?: "Section ${index + 1}"
                    val id = generateId(title, index)

                    Document.builder()
                        .id(id)
                        .text(doc.text ?: "")
                        .metadata(doc.metadata + mapOf(
                            "id" to id,
                            "sourceFile" to filename
                        ))
                        .build()
                }

                documents.addAll(processedDocs)

                // ID로 인덱싱
                for (doc in processedDocs) {
                    val docId = doc.id
                    val uniqueId = if (documentById.containsKey(docId)) {
                        "${docId}-${documents.indexOf(doc)}"
                    } else {
                        docId
                    }
                    documentById[uniqueId] = doc
                }

                // 목차 생성
                val outlineItems = processedDocs.map { doc ->
                    DocumentOutline.Item(
                        id = doc.id,
                        title = doc.metadata["title"]?.toString() ?: "",
                        level = (doc.metadata["level"] as? Int) ?: 2
                    )
                }
                outlines.add(DocumentOutline(filename, outlineItems))

                logger.info { "Loaded '$filename': ${processedDocs.size} documents" }
            } catch (e: Exception) {
                logger.error(e) { "Failed to load document: ${resource.filename}" }
            }
        }

        logger.info { "Total loaded: ${documents.size} documents from ${outlines.size} files" }
    }

    private fun generateId(title: String, index: Int): String {
        return title
            .lowercase()
            .replace(Regex("[^a-z0-9가-힣\\s-]"), "")
            .replace(Regex("\\s+"), "-")
            .replace(Regex("-+"), "-")
            .trim('-')
            .ifEmpty { "section-$index" }
    }

    fun getAllDocuments(): List<Document> = documents.toList()

    fun getDocumentById(id: String): Document? = documentById[id]

    fun getOutlines(): List<DocumentOutline> = outlines.toList()
}
