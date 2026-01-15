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

    fun search(query: String, topK: Int = 5): List<Document> {
        return vectorStore.similaritySearch(
            SearchRequest.builder()
                .query(query)
                .topK(topK)
                .build()
        )
    }
}
