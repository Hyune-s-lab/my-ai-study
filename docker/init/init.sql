-- RAG 프로젝트용 데이터베이스 생성
CREATE DATABASE rag_db;

\c rag_db

-- pgvector extension 활성화
CREATE EXTENSION IF NOT EXISTS vector;

-- vector_store 테이블 (Spring AI 스키마 + tsvector)
CREATE TABLE IF NOT EXISTS vector_store (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content TEXT,
    metadata JSONB,
    embedding vector(1536),
    content_tsv tsvector  -- Full-Text Search용
);

-- 벡터 검색 인덱스 (HNSW)
CREATE INDEX IF NOT EXISTS idx_vector_store_embedding
ON vector_store USING hnsw (embedding vector_cosine_ops);

-- Full-Text Search 인덱스 (GIN)
CREATE INDEX IF NOT EXISTS idx_vector_store_tsv
ON vector_store USING GIN(content_tsv);

-- tsvector 자동 생성 트리거
CREATE OR REPLACE FUNCTION update_content_tsv() RETURNS TRIGGER AS $$
BEGIN
    NEW.content_tsv := to_tsvector('simple', COALESCE(NEW.content, ''));
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_update_content_tsv
BEFORE INSERT OR UPDATE OF content ON vector_store
FOR EACH ROW EXECUTE FUNCTION update_content_tsv();
