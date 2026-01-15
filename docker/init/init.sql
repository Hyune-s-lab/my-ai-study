-- RAG 프로젝트용 데이터베이스 생성
CREATE DATABASE rag_db;

-- rag_db에 pgvector extension 활성화
\c rag_db
CREATE EXTENSION IF NOT EXISTS vector;

-- Full-Text Search 트리거 함수 (앱에서 트리거 생성 시 사용)
CREATE OR REPLACE FUNCTION update_content_tsv() RETURNS TRIGGER AS $$
BEGIN
  NEW.content_tsv := to_tsvector('simple', NEW.content);
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;
