-- stream_embedding 테이블 생성
-- pgvector의 vector 타입을 사용하여 임베딩 벡터 저장

CREATE TABLE IF NOT EXISTS stream_embedding (
    channel_id VARCHAR(100) PRIMARY KEY,
    embedding_text TEXT NOT NULL,
    embedding vector(1536) NOT NULL,  -- OpenAI text-embedding-3-small: 1536 dimensions
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 벡터 유사도 검색을 위한 IVFFlat 인덱스
-- cosine similarity 기반 검색 최적화
CREATE INDEX IF NOT EXISTS idx_stream_embedding_vector
ON stream_embedding
USING ivfflat (embedding vector_cosine_ops)
WITH (lists = 100);

-- 채널 ID 조회용 인덱스
CREATE INDEX IF NOT EXISTS idx_stream_embedding_channel_id
ON stream_embedding (channel_id);

COMMENT ON TABLE stream_embedding IS '방송 채널별 임베딩 벡터 저장 테이블';
COMMENT ON COLUMN stream_embedding.channel_id IS '치지직 채널 고유 ID';
COMMENT ON COLUMN stream_embedding.embedding_text IS '임베딩 생성에 사용된 원본 텍스트';
COMMENT ON COLUMN stream_embedding.embedding IS 'OpenAI 임베딩 벡터 (1536차원)';
COMMENT ON COLUMN stream_embedding.updated_at IS '마지막 업데이트 시각';