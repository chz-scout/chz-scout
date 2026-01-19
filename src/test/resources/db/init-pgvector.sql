-- pgvector 확장 활성화
CREATE EXTENSION IF NOT EXISTS vector;

-- stream_embedding 테이블 생성 (프로덕션과 동일한 스키마)
CREATE TABLE IF NOT EXISTS stream_embedding (
    channel_id VARCHAR(100) PRIMARY KEY,
    embedding_text TEXT NOT NULL,
    embedding vector(1536) NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 테스트 환경에서는 IVFFlat 인덱스를 생성하지 않음
-- IVFFlat은 빈 테이블에 생성 시 클러스터가 없어 검색 결과가 부정확해짐
-- 테스트 데이터가 적으므로 Sequential scan으로 정확한 검색 수행
