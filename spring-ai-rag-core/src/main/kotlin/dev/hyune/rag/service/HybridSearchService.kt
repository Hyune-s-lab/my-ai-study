package dev.hyune.rag.service

import dev.hyune.rag.dto.HybridSearchResult
import org.slf4j.LoggerFactory
import org.springframework.ai.vectorstore.SearchRequest
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.stereotype.Service

/**
 * 하이브리드 검색 서비스
 *
 * 벡터 검색(의미 기반) + BM25 검색(키워드 기반)을 결합하여
 * 더 정확한 검색 결과를 제공합니다.
 */
@Service
class HybridSearchService(
    private val vectorStore: VectorStore,
    private val bm25SearchService: Bm25SearchService,
) {
    private val log = LoggerFactory.getLogger(HybridSearchService::class.java)

    /**
     * 하이브리드 검색
     *
     * @param query 검색 쿼리
     * @param topK 최종 반환 개수
     * @param alpha 가중치 (0.0 = BM25 only, 1.0 = Vector only)
     */
    fun search(query: String, topK: Int = 10, alpha: Double = 0.5): List<HybridSearchResult> {
        val safeAlpha = alpha.coerceIn(0.0, 1.0)

        // 더 많이 가져와서 결합 후 topK 적용
        val fetchSize = topK * 2

        // 1. 벡터 검색
        val vectorResults = vectorStore.similaritySearch(
            SearchRequest.builder()
                .query(query)
                .topK(fetchSize)
                .similarityThreshold(0.0)
                .build()
        )

        // 2. BM25 검색
        val bm25Results = bm25SearchService.search(query, fetchSize)

        log.info(
            "[Hybrid] query='{}', vector={}, bm25={}, alpha={}",
            query.take(30), vectorResults.size, bm25Results.size, safeAlpha
        )

        // 3. 점수 맵 생성
        val vectorScores = vectorResults.associate { it.id to (it.score ?: 0.0) }
        val bm25Scores = bm25Results.associate { it.id to it.score }

        // 4. Min-Max 정규화
        val normVector = normalizeMinMax(vectorScores)
        val normBm25 = normalizeMinMax(bm25Scores)

        // 5. 컨텐츠 맵
        val contentMap = mutableMapOf<String, String>()
        vectorResults.forEach { contentMap[it.id] = it.text ?: "" }
        bm25Results.forEach { contentMap[it.id] = it.content }

        // 6. 하이브리드 점수 계산
        val allIds = (vectorScores.keys + bm25Scores.keys)

        return allIds.map { id ->
            val vScore = normVector[id] ?: 0.0
            val bScore = normBm25[id] ?: 0.0
            val hybridScore = safeAlpha * vScore + (1 - safeAlpha) * bScore

            HybridSearchResult(
                content = contentMap[id] ?: "",
                hybridScore = hybridScore,
                vectorScore = vectorScores[id] ?: 0.0,
                bm25Score = bm25Scores[id] ?: 0.0,
            )
        }
            .sortedByDescending { it.hybridScore }
            .take(topK)
    }

    /**
     * Min-Max 정규화: [0, 1] 범위로 변환
     */
    private fun normalizeMinMax(scores: Map<String, Double>): Map<String, Double> {
        if (scores.isEmpty()) return emptyMap()

        val min = scores.values.minOrNull() ?: 0.0
        val max = scores.values.maxOrNull() ?: 1.0
        val range = max - min

        return if (range == 0.0) {
            scores.mapValues { 1.0 }
        } else {
            scores.mapValues { (_, v) -> (v - min) / range }
        }
    }
}
