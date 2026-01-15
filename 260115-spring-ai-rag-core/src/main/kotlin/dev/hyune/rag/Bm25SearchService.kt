package dev.hyune.rag

import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * BM25 기반 키워드 검색 서비스
 *
 * PostgreSQL Full-Text Search를 사용하여 키워드 매칭 검색 수행.
 * ts_rank_cd (Cover Density Ranking)는 BM25와 유사한 랭킹 알고리즘.
 */
@Service
class Bm25SearchService {
    private val log = LoggerFactory.getLogger(Bm25SearchService::class.java)

    data class Bm25Result(
        val id: String,
        val content: String,
        val score: Double,
    )

    /**
     * BM25 검색 수행
     *
     * @param query 검색 쿼리 (공백으로 구분된 키워드)
     * @param topK 최대 반환 개수
     * @return BM25 점수순으로 정렬된 검색 결과
     */
    fun search(query: String, topK: Int = 10): List<Bm25Result> {
        if (query.isBlank()) return emptyList()

        return transaction {
            val sql = """
                SELECT
                    id::text,
                    content,
                    ts_rank_cd(content_tsv, plainto_tsquery('simple', ?)) as score
                FROM vector_store
                WHERE content_tsv @@ plainto_tsquery('simple', ?)
                ORDER BY score DESC
                LIMIT ?
            """.trimIndent()

            exec(sql, listOf(query, query, topK)) { rs ->
                generateSequence {
                    if (rs.next()) {
                        Bm25Result(
                            id = rs.getString("id"),
                            content = rs.getString("content") ?: "",
                            score = rs.getDouble("score"),
                        )
                    } else null
                }.toList()
            } ?: emptyList()
        }.also { results ->
            log.info(
                "[BM25] query='{}', results={}, maxScore={}",
                query.take(30),
                results.size,
                results.maxOfOrNull { it.score } ?: 0.0
            )
        }
    }

    /**
     * PreparedStatement용 exec 확장
     */
    private fun <T> exec(sql: String, args: List<Any>, transform: (java.sql.ResultSet) -> T): T? {
        val conn = org.jetbrains.exposed.sql.transactions.TransactionManager.current().connection
        return conn.prepareStatement(sql, false).let { stmt ->
            args.forEachIndexed { index, arg ->
                when (arg) {
                    is String -> stmt.set(index + 1, arg)
                    is Int -> stmt.set(index + 1, arg)
                    is Long -> stmt.set(index + 1, arg)
                    is Double -> stmt.set(index + 1, arg)
                    else -> stmt.set(index + 1, arg.toString())
                }
            }
            stmt.executeQuery().use { rs ->
                transform(rs)
            }
        }
    }

    private fun java.sql.PreparedStatement.set(index: Int, value: Any) {
        when (value) {
            is String -> setString(index, value)
            is Int -> setInt(index, value)
            is Long -> setLong(index, value)
            is Double -> setDouble(index, value)
            else -> setString(index, value.toString())
        }
    }
}
