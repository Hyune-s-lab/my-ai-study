package dev.hyune.rag.controller

import dev.hyune.rag.dto.HybridSearchResult
import dev.hyune.rag.dto.SearchResult
import dev.hyune.rag.service.HybridSearchService
import dev.hyune.rag.service.RagService
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/ai")
class RagController(
    private val ragService: RagService,
    private val hybridSearchService: HybridSearchService,
    private val jdbcTemplate: JdbcTemplate,
) {
    @PostMapping("/index")
    fun indexDocuments(@RequestBody request: IndexRequest): IndexResponse {
        val count = ragService.indexDocuments(request.documents)
        return IndexResponse(count, "문서 ${count}개가 인덱싱되었습니다.")
    }

    /**
     * RAG 기반 질의응답 API
     *
     * topK와 threshold로 검색을 제어할 수 있습니다.
     * - topK: 검색할 문서 수 (기본값: 5)
     * - threshold: 유사도 임계값 (기본값: 0.0, 범위: 0.0~1.0)
     *
     * 응답에는 검색 결과와 LLM 호출 여부가 포함되어 관찰 가능합니다.
     */
    @PostMapping("/ask")
    fun ask(@RequestBody request: AskRequest): AskResponse {
        val result = ragService.ask(
            question = request.question,
            topK = request.topK,
            threshold = request.threshold,
        )
        return AskResponse(
            question = result.question,
            answer = result.answer,
            searchResults = result.searchResults,
            llmCalled = result.llmCalled,
        )
    }

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

    data class IndexRequest(val documents: List<String>)
    data class IndexResponse(val count: Int, val message: String)

    data class AskRequest(
        val question: String,
        val topK: Int = 5,
        val threshold: Double = 0.0,
    )

    data class AskResponse(
        val question: String,
        val answer: String,
        val searchResults: List<SearchResult>,
        val llmCalled: Boolean,
    )

    data class SearchResponse(val query: String, val results: List<SearchResult>)

    /**
     * 하이브리드 검색 API (벡터 + BM25)
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

    data class HybridSearchResponse(
        val query: String,
        val alpha: Double,
        val results: List<HybridSearchResult>,
    )

    data class ClearResponse(val deleted: Int, val message: String)
}
