package dev.hyune.rag

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

    @PostMapping("/ask")
    fun ask(@RequestBody request: AskRequest): AskResponse {
        val answer = ragService.ask(request.question)
        return AskResponse(request.question, answer)
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
                    score = it.score ?: 0.0,  // 검색 결과에는 항상 score가 있어야 하지만, 방어적 처리
                    metadata = it.metadata,
                )
            }
        )
    }

    data class IndexRequest(val documents: List<String>)
    data class IndexResponse(val count: Int, val message: String)

    data class AskRequest(val question: String)
    data class AskResponse(val question: String, val answer: String)

    data class SearchResponse(val query: String, val results: List<SearchResult>)
    data class SearchResult(
        val content: String,
        val score: Double,        // 유사도 점수 (0.0 ~ 1.0, 높을수록 유사)
        val metadata: Map<String, Any>,
    )
}
