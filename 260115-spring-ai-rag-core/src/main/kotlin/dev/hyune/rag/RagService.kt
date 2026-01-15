package dev.hyune.rag

import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor
import org.springframework.ai.document.Document
import org.springframework.ai.vectorstore.SearchRequest
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.stereotype.Service

@Service
class RagService(
    private val vectorStore: VectorStore,

    chatClientBuilder: ChatClient.Builder,
) {
    private val chatClient: ChatClient = chatClientBuilder
        .defaultAdvisors(QuestionAnswerAdvisor.builder(vectorStore).build())
        .build()

    fun indexDocuments(documents: List<String>): Int {
        val docs = documents.map { Document(it) }
        vectorStore.add(docs)
        return docs.size
    }

    fun ask(question: String): String {
        return chatClient.prompt()
            .user(question)
            .call()
            .content() ?: "응답을 생성할 수 없습니다."
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
