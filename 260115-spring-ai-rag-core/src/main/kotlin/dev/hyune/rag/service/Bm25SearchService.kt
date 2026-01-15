package dev.hyune.rag.service

import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service

/**
 * BM25 기반 키워드 검색 서비스
 *
 * PostgreSQL Full-Text Search 사용.
 * ts_rank_cd (Cover Density Ranking)는 BM25와 유사한 랭킹 알고리즘.
 */
@Service
class Bm25SearchService(
    private val jdbcTemplate: JdbcTemplate,
) {
    private val log = LoggerFactory.getLogger(Bm25SearchService::class.java)

    data class Bm25Result(
        val id: String,
        val content: String,
        val score: Double,
    )

    fun search(query: String, topK: Int = 10): List<Bm25Result> {
        if (query.isBlank()) return emptyList()

        return jdbcTemplate.query(
            """
            SELECT id::text, content, ts_rank_cd(content_tsv, plainto_tsquery('simple', ?)) as score
            FROM vector_store
            WHERE content_tsv @@ plainto_tsquery('simple', ?)
            ORDER BY score DESC
            LIMIT ?
            """.trimIndent(),
            { rs, _ ->
                Bm25Result(
                    id = rs.getString("id"),
                    content = rs.getString("content") ?: "",
                    score = rs.getDouble("score"),
                )
            },
            query, query, topK
        ).also { results ->
            log.info("[BM25] query='{}', results={}", query.take(30), results.size)
        }
    }
}
