package dev.hyune.rag

import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate

/**
 * Full-Text Search 스키마 초기화
 *
 * Spring AI가 vector_store 테이블을 생성한 후,
 * tsvector 컬럼, GIN 인덱스, 트리거를 추가합니다.
 */
@Configuration
class FtsSchemaInitializer(
    private val jdbcTemplate: JdbcTemplate,
) {
    private val log = LoggerFactory.getLogger(FtsSchemaInitializer::class.java)

    @Bean
    fun initFtsSchema() = ApplicationRunner {
        try {
            // 1. tsvector 컬럼 추가
            jdbcTemplate.execute("""
                ALTER TABLE vector_store
                ADD COLUMN IF NOT EXISTS content_tsv tsvector
            """.trimIndent())
            log.info("[FTS] content_tsv 컬럼 추가 완료")

            // 2. GIN 인덱스 생성
            jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_vector_store_tsv
                ON vector_store USING GIN(content_tsv)
            """.trimIndent())
            log.info("[FTS] GIN 인덱스 생성 완료")

            // 3. 트리거 함수 생성
            jdbcTemplate.execute("""
                CREATE OR REPLACE FUNCTION update_content_tsv() RETURNS TRIGGER AS $$
                BEGIN
                  NEW.content_tsv := to_tsvector('simple', NEW.content);
                  RETURN NEW;
                END;
                $$ LANGUAGE plpgsql
            """.trimIndent())

            // 4. 트리거 생성 (이미 존재하면 무시)
            jdbcTemplate.execute("""
                DO $$
                BEGIN
                    IF NOT EXISTS (
                        SELECT 1 FROM pg_trigger WHERE tgname = 'trg_update_content_tsv'
                    ) THEN
                        CREATE TRIGGER trg_update_content_tsv
                        BEFORE INSERT OR UPDATE OF content ON vector_store
                        FOR EACH ROW EXECUTE FUNCTION update_content_tsv();
                    END IF;
                END $$
            """.trimIndent())
            log.info("[FTS] 트리거 생성 완료")

            // 5. 기존 데이터 마이그레이션 (tsvector가 null인 행)
            val updated = jdbcTemplate.update("""
                UPDATE vector_store
                SET content_tsv = to_tsvector('simple', content)
                WHERE content_tsv IS NULL
            """.trimIndent())
            if (updated > 0) {
                log.info("[FTS] 기존 데이터 {} 건 마이그레이션 완료", updated)
            }

            log.info("[FTS] Full-Text Search 스키마 초기화 완료")
        } catch (e: Exception) {
            log.error("[FTS] 스키마 초기화 실패: {}", e.message)
        }
    }
}
