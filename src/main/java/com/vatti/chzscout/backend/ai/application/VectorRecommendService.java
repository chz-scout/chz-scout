package com.vatti.chzscout.backend.ai.application;

import com.vatti.chzscout.backend.ai.application.usecase.VectorRecommendUseCase;
import com.vatti.chzscout.backend.ai.domain.dto.StreamEmbeddingWithSimilarity;
import com.vatti.chzscout.backend.ai.infrastructure.EmbeddingClient;
import com.vatti.chzscout.backend.ai.infrastructure.StreamEmbeddingRepository;
import com.vatti.chzscout.backend.stream.domain.EnrichedStreamDto;
import com.vatti.chzscout.backend.stream.domain.Stream;
import com.vatti.chzscout.backend.stream.infrastructure.redis.StreamRedisStore;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
  private final StreamRedisStore streamRedisStore;

  private static final int DEFAULT_LIMIT = 5;

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
