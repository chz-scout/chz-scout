package com.vatti.chzscout.backend.ai.infrastructure;

import com.vatti.chzscout.backend.ai.domain.dto.StreamEmbeddingWithSimilarity;
import com.vatti.chzscout.backend.ai.domain.entity.StreamEmbedding;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 방송 임베딩 벡터 저장소.
 *
 * <p>pgvector를 사용하여 벡터 유사도 검색을 지원합니다.
 */
public interface StreamEmbeddingRepository extends JpaRepository<StreamEmbedding, String> {

  /**
   * 쿼리 벡터와 유사한 방송 임베딩을 검색합니다.
   *
   * @param queryEmbedding 검색할 쿼리 벡터 (문자열 형식: "[0.1, 0.2, ...]")
   * @param limit 반환할 최대 결과 수
   * @return 유사도가 높은 순으로 정렬된 결과 목록
   */
  @Query(
      value =
          "SELECT "
              + "  se.channel_id     AS channelId, "
              + "  se.embedding_text AS embeddingText, "
              + "  se.updated_at     AS updatedAt, "
              + "  1 - (se.embedding <=> CAST(:queryEmbedding AS vector)) AS similarity "
              + "FROM stream_embedding se "
              + "ORDER BY se.embedding <=> CAST(:queryEmbedding AS vector) "
              + "LIMIT :limit",
      nativeQuery = true)
  List<StreamEmbeddingWithSimilarity> findSimilarEmbeddings(
      @Param("queryEmbedding") String queryEmbedding, @Param("limit") int limit);

  /**
   * 쿼리 벡터로 1차 필터링 후, 쿼리+선호 가중치 혼합 점수로 정렬하여 개인화 추천합니다.
   *
   * <p>최종 점수 = (1 - preferenceWeight) * 쿼리유사도 + preferenceWeight * 선호유사도
   *
   * @param queryEmbedding 검색할 쿼리 벡터 (문자열 형식: "[0.1, 0.2, ...]")
   * @param preferenceEmbedding 사용자 선호 벡터 (문자열 형식: "[0.1, 0.2, ...]")
   * @param threshold 쿼리 유사도 최소 임계값 (0.0 ~ 1.0)
   * @param preferenceWeight 선호 벡터 가중치 (0.0 ~ 1.0)
   * @param limit 반환할 최대 결과 수
   * @return 가중치 혼합 점수 순으로 정렬된 결과 목록
   */
  @Query(
      value =
          "SELECT "
              + "  se.channel_id     AS channelId, "
              + "  se.embedding_text AS embeddingText, "
              + "  se.updated_at     AS updatedAt, "
              + "  (1 - :preferenceWeight) * (1 - (se.embedding <=> CAST(:queryEmbedding AS vector))) "
              + "    + :preferenceWeight * (1 - (se.embedding <=> CAST(:preferenceEmbedding AS vector))) AS similarity "
              + "FROM stream_embedding se "
              + "WHERE 1 - (se.embedding <=> CAST(:queryEmbedding AS vector)) >= :threshold "
              + "ORDER BY similarity DESC "
              + "LIMIT :limit",
      nativeQuery = true)
  List<StreamEmbeddingWithSimilarity> findPersonalizedRecommendations(
      @Param("queryEmbedding") String queryEmbedding,
      @Param("preferenceEmbedding") String preferenceEmbedding,
      @Param("threshold") double threshold,
      @Param("preferenceWeight") double preferenceWeight,
      @Param("limit") int limit);

  /**
   * 특정 채널 ID 목록에 해당하는 임베딩을 삭제합니다.
   *
   * <p>변경된 방송의 임베딩 동기화에 사용됩니다.
   *
   * @param channelIds 삭제할 채널 ID 목록
   */
  @Modifying
  @Query("DELETE FROM StreamEmbedding se WHERE se.channelId IN :channelIds")
  void deleteByChannelIdIn(@Param("channelIds") List<String> channelIds);
}
