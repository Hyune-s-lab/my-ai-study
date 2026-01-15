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
            results = documents.map { SearchResult(it.text ?: "", it.metadata) }
        )
    }

    data class IndexRequest(val documents: List<String>)
    data class IndexResponse(val count: Int, val message: String)

    data class AskRequest(val question: String)
    data class AskResponse(val question: String, val answer: String)

    data class SearchResponse(val query: String, val results: List<SearchResult>)
    data class SearchResult(val content: String, val metadata: Map<String, Any>)
}
