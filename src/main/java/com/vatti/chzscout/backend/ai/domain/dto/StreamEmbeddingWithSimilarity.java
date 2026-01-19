package com.vatti.chzscout.backend.ai.domain.dto;

import java.time.LocalDateTime;

/**
 * 벡터 유사도 검색 결과를 담는 Projection 인터페이스.
 *
 * <p>StreamEmbedding 엔티티 필드와 계산된 유사도(similarity)를 함께 반환합니다.
 */
public interface StreamEmbeddingWithSimilarity {

  String getChannelId();

  String getEmbeddingText();

  LocalDateTime getUpdatedAt();

  /**
   * 코사인 유사도 (0.0 ~ 1.0).
   *
   * <p>1에 가까울수록 유사함을 의미합니다.
   */
  Double getSimilarity();
}
