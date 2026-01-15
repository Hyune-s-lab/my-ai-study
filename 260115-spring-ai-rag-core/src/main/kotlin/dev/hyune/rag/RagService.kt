package dev.hyune.rag

import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.document.Document
import org.springframework.ai.vectorstore.SearchRequest
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.stereotype.Service

@Service
class RagService(
    private val vectorStore: VectorStore,
    chatClientBuilder: ChatClient.Builder,
) {
    private val log = LoggerFactory.getLogger(RagService::class.java)

    // QuestionAnswerAdvisor 없이 기본 ChatClient 사용 (수동 RAG를 위해)
    private val chatClient: ChatClient = chatClientBuilder.build()

    fun indexDocuments(documents: List<String>): Int {
        val docs = documents.map { Document(it) }
        vectorStore.add(docs)
        return docs.size
    }

    /**
     * RAG 기반 질의응답
     *
     * 1. 벡터 검색으로 관련 문서 찾기
     * 2. 검색 결과가 없으면 LLM 호출하지 않음
     * 3. 검색된 문서를 컨텍스트로 LLM에 전달
     *
     * @param question 질문
     * @param topK 최대 검색 문서 수
     * @param threshold 유사도 임계값 (이 값 이상인 문서만 사용)
     */
    fun ask(question: String, topK: Int = 5, threshold: Double = 0.0): AskResult {
        // 1. 벡터 검색
        val documents = search(question, topK, threshold)

        // 2. 로깅 (관찰 가능성)
        val scores = documents.mapNotNull { it.score }
        log.info(
            "[RAG 검색] query='{}', topK={}, threshold={}, 결과={}개, 최고score={}, 평균score={}",
            question.take(50),
            topK,
            threshold,
            documents.size,
            scores.maxOrNull() ?: 0.0,
            if (scores.isNotEmpty()) scores.average() else 0.0
        )

        // 3. 검색 결과가 없으면 LLM 호출하지 않음
        if (documents.isEmpty()) {
            log.info("[RAG] 검색 결과 없음 → LLM 호출 생략")
            return AskResult(
                question = question,
                answer = "관련 문서를 찾을 수 없습니다.",
                searchResults = emptyList(),
                llmCalled = false,
            )
        }

        // 4. 컨텍스트 조립
        val context = documents.joinToString("\n\n") { it.text ?: "" }

        // 5. LLM 호출
        val answer = chatClient.prompt()
            .system(SYSTEM_PROMPT)
            .user("컨텍스트:\n$context\n\n질문: $question")
            .call()
            .content() ?: "응답을 생성할 수 없습니다."

        return AskResult(
            question = question,
            answer = answer,
            searchResults = documents.map {
                SearchResultDto(
                    content = it.text ?: "",
                    score = it.score ?: 0.0,
                )
            },
            llmCalled = true,
        )
    }

    companion object {
        /**
         * RAG 시스템 프롬프트
         * - 검색된 문서만 근거로 답변
         * - 추측 금지
         */
        private val SYSTEM_PROMPT = """
            당신은 검색된 문서를 기반으로 답변하는 AI 어시스턴트입니다.

            규칙:
            1. 제공된 컨텍스트에 있는 정보만 사용하여 답변하세요.
            2. 컨텍스트에 없는 내용은 "해당 정보를 찾을 수 없습니다"라고 답변하세요.
            3. 추측하지 마세요.
            4. 답변은 간결하고 명확하게 작성하세요.
        """.trimIndent()
    }

    /**
     * 벡터 유사도 검색
     *
     * @param query 검색 쿼리
     * @param topK 최대 반환 개수
     * @param threshold 유사도 임계값 (0.0 ~ 1.0). 이 값 이상인 문서만 반환
     */
    fun search(query: String, topK: Int = 5, threshold: Double = 0.0): List<Document> {
        return vectorStore.similaritySearch(
            SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThreshold(threshold)
                .build()
        )
    }
}
