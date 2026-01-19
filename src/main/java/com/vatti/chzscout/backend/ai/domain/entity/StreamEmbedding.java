package com.vatti.chzscout.backend.ai.domain.entity;

import com.vatti.chzscout.backend.ai.infrastructure.type.VectorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

/**
 * 방송 임베딩 벡터 엔티티.
 *
 * <p>pgvector를 사용하여 방송 정보의 임베딩 벡터를 저장합니다.
 *
 * <p>테이블은 수동 생성됩니다 (docker/postgres/init/02-create-stream-embedding.sql)
 */
@Entity
@Table(name = "stream_embedding")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StreamEmbedding {

  @Id
  @Column(name = "channel_id", length = 100)
  private String channelId;

  @Column(name = "embedding_text", nullable = false, columnDefinition = "TEXT")
  private String embeddingText;

  @Type(VectorType.class)
  @Column(name = "embedding", nullable = false, columnDefinition = "vector(1536)")
  private float[] embedding;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  private StreamEmbedding(String channelId, String embeddingText, float[] embedding) {
    this.channelId = channelId;
    this.embeddingText = embeddingText;
    this.embedding = embedding;
    this.updatedAt = LocalDateTime.now();
  }

  public static StreamEmbedding create(String channelId, String embeddingText, float[] embedding) {
    return new StreamEmbedding(channelId, embeddingText, embedding);
  }

  public void updateEmbedding(String embeddingText, float[] embedding) {
    this.embeddingText = embeddingText;
    this.embedding = embedding;
    this.updatedAt = LocalDateTime.now();
  }
}
