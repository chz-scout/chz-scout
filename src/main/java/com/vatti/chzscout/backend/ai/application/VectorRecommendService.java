package com.vatti.chzscout.backend.ai.application;

import com.vatti.chzscout.backend.ai.application.usecase.VectorRecommendUseCase;
import com.vatti.chzscout.backend.ai.domain.dto.MemberEmbeddingVectorResult;
import com.vatti.chzscout.backend.ai.domain.dto.StreamEmbeddingWithSimilarity;
import com.vatti.chzscout.backend.ai.infrastructure.EmbeddingClient;
import com.vatti.chzscout.backend.ai.infrastructure.StreamEmbeddingRepository;
import com.vatti.chzscout.backend.member.domain.entity.Member;
import com.vatti.chzscout.backend.stream.domain.EnrichedStreamDto;
import com.vatti.chzscout.backend.stream.domain.Stream;
import com.vatti.chzscout.backend.stream.infrastructure.redis.StreamRedisStore;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 벡터 임베딩 기반 방송 추천 서비스.
 *
 * <p>사용자 쿼리를 임베딩하고 pgvector로 유사 방송을 검색합니다.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class VectorRecommendService implements VectorRecommendUseCase {

  private final StreamEmbeddingRepository streamEmbeddingRepository;
  private final EmbeddingClient embeddingClient;
  private final EmbeddingService embeddingService;
  private final StreamRedisStore streamRedisStore;

  private static final int DEFAULT_LIMIT = 5;

  /** 1차 필터링 시 쿼리 유사도 최소 임계값. */
  @Value("${recommendation.threshold:0.3}")
  private double similarityThreshold;

  @Override
  public List<Stream> recommend(String message, int limit) {
    if (message == null || message.isBlank()) {
      log.warn("빈 쿼리로 추천 요청");
      return List.of();
    }

    int effectiveLimit = limit > 0 ? limit : DEFAULT_LIMIT;
    log.info("벡터 추천 요청 - message: '{}', limit: {}", message, effectiveLimit);

    // 1. 쿼리 임베딩 생성
    float[] queryEmbedding = embeddingClient.embed(message);
    String embeddingString = toVectorString(queryEmbedding);

    // 2. pgvector 유사도 검색
    List<StreamEmbeddingWithSimilarity> similarEmbeddings =
        streamEmbeddingRepository.findSimilarEmbeddings(embeddingString, effectiveLimit);

    if (similarEmbeddings.isEmpty()) {
      log.info("유사한 방송 없음");
      return List.of();
    }

    // 3. Redis에서 실제 방송 정보 조회
    List<String> channelIds =
        similarEmbeddings.stream().map(StreamEmbeddingWithSimilarity::getChannelId).toList();

    Map<String, EnrichedStreamDto> streamMap =
        streamRedisStore.findEnrichedStreams().stream()
            .filter(s -> channelIds.contains(s.channelId()))
            .collect(Collectors.toMap(EnrichedStreamDto::channelId, Function.identity()));

    // 4. 유사도 순서 유지하며 Stream 반환
    List<Stream> results =
        similarEmbeddings.stream()
            .filter(e -> streamMap.containsKey(e.getChannelId()))
            .map(e -> Stream.from(streamMap.get(e.getChannelId())))
            .toList();

    log.info(
        "벡터 추천 완료 - {}개 결과, 최고 유사도: {}",
        results.size(),
        similarEmbeddings.getFirst().getSimilarity());

    return results;
  }

  @Override
  public List<Stream> recommend(String message, Member member, int limit) {
    if (message == null || message.isBlank()) {
      log.warn("빈 쿼리로 추천 요청");
      return List.of();
    }

    int effectiveLimit = limit > 0 ? limit : DEFAULT_LIMIT;
    log.info(
        "개인화 벡터 추천 요청 - message: '{}', member: {}, limit: {}",
        message,
        member.getUuid(),
        effectiveLimit);

    // 1. 쿼리 임베딩 생성
    float[] queryEmbedding = embeddingClient.embed(message);
    String queryEmbeddingString = toVectorString(queryEmbedding);

    // 2. 사용자 선호 벡터 조회
    MemberEmbeddingVectorResult preferenceResult =
        embeddingService.createMemberEmbeddingVector(member);

    List<StreamEmbeddingWithSimilarity> results;

    if (preferenceResult != null) {
      // 3. DB에서 1차 필터링 + 2차 정렬 한 번에 처리
      String preferenceEmbeddingString = toVectorString(preferenceResult.embedding());

      results =
          streamEmbeddingRepository.findPersonalizedRecommendations(
              queryEmbeddingString, preferenceEmbeddingString, similarityThreshold, effectiveLimit);

      log.debug("개인화 추천 완료 - 선호 벡터 적용");
    } else {
      // 선호 벡터 없으면 쿼리만으로 검색
      results =
          streamEmbeddingRepository.findSimilarEmbeddings(queryEmbeddingString, effectiveLimit);

      log.debug("선호 벡터 없음 - 쿼리만 사용");
    }

    if (results.isEmpty()) {
      log.info("유사한 방송 없음 (threshold: {})", similarityThreshold);
      return List.of();
    }

    // 4. Redis에서 실제 방송 정보 조회
    List<String> channelIds =
        results.stream().map(StreamEmbeddingWithSimilarity::getChannelId).toList();

    Map<String, EnrichedStreamDto> streamMap =
        streamRedisStore.findEnrichedStreams().stream()
            .filter(s -> channelIds.contains(s.channelId()))
            .collect(Collectors.toMap(EnrichedStreamDto::channelId, Function.identity()));

    // 5. 정렬 순서 유지하며 Stream 반환
    List<Stream> streamResults =
        channelIds.stream()
            .filter(streamMap::containsKey)
            .map(channelId -> Stream.from(streamMap.get(channelId)))
            .toList();

    log.info("개인화 벡터 추천 완료 - {}개 결과", streamResults.size());

    return streamResults;
  }

  /**
   * float 배열을 pgvector 문자열 형식으로 변환합니다.
   *
   * @param embedding 임베딩 벡터
   * @return "[0.1,0.2,...]" 형식의 문자열
   */
  private String toVectorString(float[] embedding) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < embedding.length; i++) {
      if (i > 0) sb.append(",");
      sb.append(embedding[i]);
    }
    sb.append("]");
    return sb.toString();
  }
}
