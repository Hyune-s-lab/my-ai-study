package dev.hyune.rag.controller

import dev.hyune.rag.dto.SearchResult
import dev.hyune.rag.service.RagService
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/ai")
class RagController(
    private val ragService: RagService,
) {
    @PostMapping("/index")
    fun indexDocuments(@RequestBody request: IndexRequest): IndexResponse {
        val count = ragService.indexDocuments(request.documents)
        return IndexResponse(count, "문서 ${count}개가 인덱싱되었습니다.")
    }

    /**
     * RAG 기반 질의응답 API (검색 + LLM 호출)
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
}
