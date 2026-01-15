-- RAG 프로젝트용 데이터베이스 생성
CREATE DATABASE rag_db;

-- rag_db에 pgvector extension 활성화
\c rag_db
CREATE EXTENSION IF NOT EXISTS vector;
