package com.vatti.chzscout.backend.ai.domain.dto;

/**
 * 멤버의 통합 선호 벡터 결과.
 *
 * <p>태그 임베딩과 좋아요 임베딩을 합산하여 정규화한 벡터입니다.
 */
public record MemberEmbeddingVectorResult(String uuid, float[] embedding) {

  public static MemberEmbeddingVectorResult of(String uuid, float[] embedding) {
    return new MemberEmbeddingVectorResult(uuid, embedding);
  }
}
