package dev.hyune.rag.controller

import dev.hyune.rag.dto.HybridSearchResult
import dev.hyune.rag.dto.SearchResult
import dev.hyune.rag.service.HybridSearchService
import dev.hyune.rag.service.RagService
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/ai")
class SearchController(
    private val ragService: RagService,
    private val hybridSearchService: HybridSearchService,
    private val jdbcTemplate: JdbcTemplate,
) {
    /**
     * 벡터 유사도 검색 (LLM 호출 없음)
     */
    @GetMapping("/search")
    fun search(
        @RequestParam query: String,
        @RequestParam(defaultValue = "5") topK: Int,
        @RequestParam(defaultValue = "0.0") threshold: Double,
    ): SearchResponse {
        val documents = ragService.search(query, topK, threshold)
        return SearchResponse(
            query = query,
            results = documents.map {
                SearchResult(
                    content = it.text ?: "",
                    score = it.score ?: 0.0,
                    metadata = it.metadata,
                )
            }
        )
    }

    /**
     * 하이브리드 검색 (벡터 + BM25, LLM 호출 없음)
     *
     * @param alpha 가중치 (0.0 = BM25 only, 1.0 = Vector only, 기본값: 0.5)
     */
    @GetMapping("/hybrid")
    fun hybridSearch(
        @RequestParam query: String,
        @RequestParam(defaultValue = "10") topK: Int,
        @RequestParam(defaultValue = "0.5") alpha: Double,
    ): HybridSearchResponse {
        val results = hybridSearchService.search(query, topK, alpha)
        return HybridSearchResponse(
            query = query,
            alpha = alpha,
            results = results,
        )
    }

    /**
     * 데이터 초기화 (테스트용)
     */
    @DeleteMapping("/clear")
    fun clear(): ClearResponse {
        val deleted = jdbcTemplate.update("DELETE FROM vector_store")
        return ClearResponse(deleted, "문서 ${deleted}개가 삭제되었습니다.")
    }

    data class SearchResponse(val query: String, val results: List<SearchResult>)

    data class HybridSearchResponse(
        val query: String,
        val alpha: Double,
        val results: List<HybridSearchResult>,
    )

    data class ClearResponse(val deleted: Int, val message: String)
}
