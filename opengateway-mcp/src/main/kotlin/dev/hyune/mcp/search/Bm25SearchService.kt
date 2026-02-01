package dev.hyune.mcp.search

import dev.hyune.mcp.document.DocumentStore
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.ai.document.Document
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import kotlin.math.ln

/**
 * 인메모리 BM25 검색 서비스 (학습용 직접 구현)
 *
 * ## 프로덕션 대안
 * - SQLite FTS5: 가볍고 검증됨
 * - Lucene (인메모리): 풀텍스트 검색 표준
 * - DuckDB: 분석 쿼리에 강점
 *
 * ## BM25 알고리즘
 * score(D, Q) = Σ IDF(qi) × (TF × (k1 + 1)) / (TF + k1 × (1 - b + b × |D|/avgdl))
 */
@Service
class Bm25SearchService(
    private val documentStore: DocumentStore
) {
    private val logger = KotlinLogging.logger {}

    // BM25 파라미터
    private val k1 = 1.5
    private val b = 0.75

    // 역인덱스: 토큰 → (문서ID → 출현 횟수)
    private val invertedIndex = mutableMapOf<String, MutableMap<String, Int>>()
    private val docLengths = mutableMapOf<String, Int>()
    private var avgDocLength = 0.0
    private var totalDocs = 0

    // 문서 ID로 조회
    private val docIndex = mutableMapOf<String, Document>()

    @EventListener(ApplicationReadyEvent::class)
    fun buildIndex() {
        logger.info { "Building BM25 inverted index..." }

        val documents = documentStore.getAllDocuments()
        totalDocs = documents.size

        docIndex.clear()
        documents.forEach { docIndex[it.id] = it }

        if (totalDocs == 0) {
            logger.warn { "No documents to index" }
            return
        }

        var totalTokens = 0

        for (doc in documents) {
            val docId = doc.id
            val title = doc.metadata["title"]?.toString() ?: ""
            val searchableText = "$title ${doc.text}"
            val tokens = tokenize(searchableText)
            docLengths[docId] = tokens.size
            totalTokens += tokens.size

            val termFreq = tokens.groupingBy { it }.eachCount()
            for ((term, count) in termFreq) {
                invertedIndex.getOrPut(term) { mutableMapOf() }[docId] = count
            }
        }

        avgDocLength = totalTokens.toDouble() / totalDocs
        logger.info { "Index built: $totalDocs docs, ${invertedIndex.size} unique terms" }
    }

    fun search(query: String, limit: Int = 5): List<SearchResult> {
        if (query.isBlank()) return emptyList()

        val queryTokens = tokenize(query)
        if (queryTokens.isEmpty()) return emptyList()

        val scores = mutableMapOf<String, Double>()
        val matchedTermsMap = mutableMapOf<String, MutableSet<String>>()

        for (term in queryTokens) {
            val postings = invertedIndex[term] ?: continue
            val idf = calculateIdf(postings.size)

            for ((docId, tf) in postings) {
                val docLength = docLengths[docId] ?: continue
                val tfScore = calculateTfScore(tf, docLength)
                scores[docId] = (scores[docId] ?: 0.0) + idf * tfScore
                matchedTermsMap.getOrPut(docId) { mutableSetOf() }.add(term)
            }
        }

        return scores.entries
            .sortedByDescending { it.value }
            .take(limit)
            .mapNotNull { (docId, score) ->
                docIndex[docId]?.let { doc ->
                    SearchResult(
                        document = doc,
                        score = score,
                        matchedTerms = matchedTermsMap[docId]?.toList() ?: emptyList()
                    )
                }
            }
    }

    private fun tokenize(text: String): List<String> {
        return text
            .lowercase()
            .split(Regex("[\\s\\p{Punct}]+"))
            .filter { it.length >= 2 }
            .filter { !STOP_WORDS.contains(it) }
    }

    private fun calculateIdf(docFreq: Int): Double {
        return ln((totalDocs - docFreq + 0.5) / (docFreq + 0.5) + 1)
    }

    private fun calculateTfScore(tf: Int, docLength: Int): Double {
        val lengthNorm = 1 - b + b * (docLength / avgDocLength)
        return (tf * (k1 + 1)) / (tf + k1 * lengthNorm)
    }

    companion object {
        private val STOP_WORDS = setOf(
            "the", "a", "an", "is", "are", "was", "were", "be", "been",
            "being", "have", "has", "had", "do", "does", "did", "will",
            "would", "could", "should", "may", "might", "can", "this",
            "that", "these", "those", "it", "its", "of", "in", "to",
            "for", "on", "with", "at", "by", "from", "as", "or", "and",
            "은", "는", "이", "가", "을", "를", "의", "에", "에서",
            "으로", "로", "와", "과", "도", "만", "등", "및"
        )
    }

    /**
     * BM25 검색 결과
     */
    data class SearchResult(
        val document: Document,
        val score: Double,
        val matchedTerms: List<String>
    )
}
