package dev.hyune.mcp.search

import dev.hyune.mcp.document.DocumentChunk
import dev.hyune.mcp.document.DocumentStore
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Service
import kotlin.math.ln

private val logger = KotlinLogging.logger {}

/**
 * BM25 검색 결과
 */
data class SearchResult(
    val chunk: DocumentChunk,
    val score: Double,
    /** 검색어와 매칭된 토큰들 */
    val matchedTerms: List<String>
)

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
 * 
 * ## 동작 방식
 * 1. 애플리케이션 시작 시 역인덱스 구축
 * 2. 검색 쿼리를 토큰화
 * 3. 각 토큰의 역인덱스에서 관련 문서 조회
 * 4. BM25 점수 계산 후 정렬
 */
@Service
class Bm25SearchService(
    private val documentStore: DocumentStore
) {
    // BM25 파라미터
    private val k1 = 1.5  // TF 포화 속도 (1.2 ~ 2.0 권장)
    private val b = 0.75  // 문서 길이 보정 (0.75 권장)
    
    // 역인덱스: 토큰 → (청크ID → 출현 횟수)
    private val invertedIndex = mutableMapOf<String, MutableMap<String, Int>>()
    
    // 문서별 토큰 수
    private val docLengths = mutableMapOf<String, Int>()
    
    // 평균 문서 길이
    private var avgDocLength = 0.0
    
    // 전체 문서 수
    private var totalDocs = 0
    
    @PostConstruct
    fun buildIndex() {
        logger.info { "Building BM25 inverted index..." }
        
        val chunks = documentStore.getAllChunks()
        totalDocs = chunks.size
        
        if (totalDocs == 0) {
            logger.warn { "No documents to index" }
            return
        }
        
        var totalTokens = 0
        
        for (chunk in chunks) {
            val tokens = tokenize(chunk.content)
            docLengths[chunk.id] = tokens.size
            totalTokens += tokens.size
            
            // 토큰 빈도 계산
            val termFreq = tokens.groupingBy { it }.eachCount()
            
            // 역인덱스에 추가
            for ((term, count) in termFreq) {
                invertedIndex
                    .getOrPut(term) { mutableMapOf() }
                    .put(chunk.id, count)
            }
        }
        
        avgDocLength = totalTokens.toDouble() / totalDocs
        
        logger.info { 
            "Index built: $totalDocs docs, ${invertedIndex.size} unique terms, avgDocLength=${"%.1f".format(avgDocLength)}" 
        }
    }
    
    /**
     * BM25 검색 수행
     * 
     * @param query 검색 쿼리
     * @param limit 최대 결과 수
     * @return 점수 내림차순 정렬된 검색 결과
     */
    fun search(query: String, limit: Int = 5): List<SearchResult> {
        if (query.isBlank()) return emptyList()
        
        val queryTokens = tokenize(query)
        if (queryTokens.isEmpty()) return emptyList()
        
        // 각 문서의 점수 계산
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
        
        // 점수순 정렬 및 결과 생성
        return scores.entries
            .sortedByDescending { it.value }
            .take(limit)
            .mapNotNull { (docId, score) ->
                documentStore.getChunkById(docId)?.let { chunk ->
                    SearchResult(
                        chunk = chunk,
                        score = score,
                        matchedTerms = matchedTermsMap[docId]?.toList() ?: emptyList()
                    )
                }
            }
            .also { results ->
                logger.debug { "Search '$query' → ${results.size} results" }
            }
    }
    
    /**
     * 텍스트를 토큰으로 분할
     * 
     * MVP 구현: 공백/특수문자 기준 분할 + 소문자 변환
     * TODO: 형태소 분석기 추가 시 한국어 검색 품질 향상 가능
     */
    private fun tokenize(text: String): List<String> {
        return text
            .lowercase()
            .split(Regex("[\\s\\p{Punct}]+"))  // 공백, 구두점으로 분할
            .filter { it.length >= 2 }          // 2글자 이상만
            .filter { !STOP_WORDS.contains(it) } // 불용어 제거
    }
    
    /**
     * IDF (Inverse Document Frequency) 계산
     * IDF = ln((N - n + 0.5) / (n + 0.5) + 1)
     * 
     * @param docFreq 해당 단어가 등장한 문서 수
     */
    private fun calculateIdf(docFreq: Int): Double {
        return ln((totalDocs - docFreq + 0.5) / (docFreq + 0.5) + 1)
    }
    
    /**
     * TF 점수 계산 (문서 길이 보정 포함)
     * TF_score = (TF × (k1 + 1)) / (TF + k1 × (1 - b + b × |D|/avgdl))
     */
    private fun calculateTfScore(tf: Int, docLength: Int): Double {
        val lengthNorm = 1 - b + b * (docLength / avgDocLength)
        return (tf * (k1 + 1)) / (tf + k1 * lengthNorm)
    }
    
    companion object {
        /** 검색에서 무시할 불용어 */
        private val STOP_WORDS = setOf(
            // 영어
            "the", "a", "an", "is", "are", "was", "were", "be", "been",
            "being", "have", "has", "had", "do", "does", "did", "will",
            "would", "could", "should", "may", "might", "can", "this",
            "that", "these", "those", "it", "its", "of", "in", "to",
            "for", "on", "with", "at", "by", "from", "as", "or", "and",
            // 한국어 (자주 쓰이지만 의미 없는)
            "은", "는", "이", "가", "을", "를", "의", "에", "에서",
            "으로", "로", "와", "과", "도", "만", "등", "및"
        )
    }
}
