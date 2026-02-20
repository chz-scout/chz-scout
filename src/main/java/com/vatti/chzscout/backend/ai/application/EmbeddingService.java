package com.vatti.chzscout.backend.ai.application;

import com.vatti.chzscout.backend.ai.domain.dto.MemberEmbeddingVectorResult;
import com.vatti.chzscout.backend.ai.domain.entity.MemberEmbedding;
import com.vatti.chzscout.backend.ai.domain.entity.StreamEmbedding;
import com.vatti.chzscout.backend.ai.infrastructure.EmbeddingClient;
import com.vatti.chzscout.backend.ai.infrastructure.MemberEmbeddingRepository;
import com.vatti.chzscout.backend.member.domain.entity.Member;
import com.vatti.chzscout.backend.stream.domain.AllFieldLiveDto;
import com.vatti.chzscout.backend.tag.application.service.MemberTagService;
import com.vatti.chzscout.backend.tag.domain.dto.MemberTagListResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 임베딩 생성 서비스.
 *
 * <p>방송 정보를 텍스트로 변환하고 임베딩 벡터를 생성합니다. Virtual Thread를 활용하여 배치 처리를 효율적으로 수행합니다.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EmbeddingService {

  private final EmbeddingClient embeddingClient;
  private final ExecutorService aiExecutor;
  private final MemberEmbeddingRepository memberEmbeddingRepository;
  private final MemberTagService memberTagService;

  /** 배치 처리 시 한 번에 처리할 방송 수. OpenAI API 제한 고려 (최대 2048개). */
  private static final int BATCH_CHUNK_SIZE = 100;

  /** 태그 임베딩 가중치 (0.0 ~ 1.0). */
  @Value("${recommendation.weight.tag:0.3}")
  private float tagWeight;

  /** 좋아요 임베딩 가중치 (0.0 ~ 1.0). */
  @Value("${recommendation.weight.like:0.7}")
  private float likeWeight;

  /**
   * 단일 방송 정보의 임베딩을 생성합니다.
   *
   * @param stream 방송 정보
   * @return StreamEmbedding 엔티티
   */
  public StreamEmbedding createEmbedding(AllFieldLiveDto stream) {
    String embeddingText = toEmbeddingText(stream);
    float[] embedding = embeddingClient.embed(embeddingText);

    return StreamEmbedding.create(stream.channelId(), embeddingText, embedding);
  }

  /**
   * 멤버의 통합 선호 벡터를 생성합니다.
   *
   * <p>태그 임베딩과 좋아요 임베딩을 가중 합산하여 정규화한 벡터를 반환합니다. 가중치는 recommendation.weight.tag,
   * recommendation.weight.like 설정으로 조절 가능합니다.
   *
   * @param member 멤버 엔티티
   * @return 통합 선호 벡터 결과 (태그/좋아요가 없으면 null)
   */
  public MemberEmbeddingVectorResult createMemberEmbeddingVector(Member member) {
    List<MemberEmbedding> likeEmbeddings =
        memberEmbeddingRepository.findTop50ByMemberOrderByUpdatedAtDesc(member);
    MemberTagListResponse memberTags = memberTagService.getMemberTags(member.getUuid());

    // 좋아요 평균 벡터
    float[] likeAverage = null;
    if (!likeEmbeddings.isEmpty()) {
      List<float[]> likeVectors =
          likeEmbeddings.stream().map(MemberEmbedding::getEmbedding).toList();
      likeAverage = averageVectors(likeVectors);
    }

    // 태그 임베딩
    float[] tagEmbedding = null;
    String tagText = buildTagText(memberTags);
    if (!tagText.isBlank()) {
      tagEmbedding = embeddingClient.embed(tagText);
    }

    // 둘 다 없으면 null
    if (likeAverage == null && tagEmbedding == null) {
      return null;
    }

    // 가중 합산
    float[] combined = weightedCombine(tagEmbedding, likeAverage);
    float[] normalized = normalize(combined);

    return MemberEmbeddingVectorResult.of(member.getUuid(), normalized);
  }

  private float[] weightedCombine(float[] tagVector, float[] likeVector) {
    // 한쪽만 있으면 그대로 반환
    if (tagVector == null) {
      return likeVector;
    }
    if (likeVector == null) {
      return tagVector;
    }

    // 가중 합산
    int dimension = tagVector.length;
    float[] result = new float[dimension];

    for (int i = 0; i < dimension; i++) {
      result[i] = (tagWeight * tagVector[i]) + (likeWeight * likeVector[i]);
    }

    return result;
  }

  private String buildTagText(MemberTagListResponse memberTags) {
    List<String> tagNames = new ArrayList<>();

    memberTags.customTags().forEach(tag -> tagNames.add(tag.tagName()));
    memberTags.categoryTags().forEach(tag -> tagNames.add(tag.tagName()));

    return String.join(", ", tagNames);
  }

  private float[] averageVectors(List<float[]> vectors) {
    int dimension = vectors.get(0).length;
    float[] result = new float[dimension];

    for (float[] vector : vectors) {
      for (int i = 0; i < dimension; i++) {
        result[i] += vector[i];
      }
    }

    for (int i = 0; i < dimension; i++) {
      result[i] /= vectors.size();
    }

    return result;
  }

  private float[] normalize(float[] vector) {
    float magnitude = 0;
    for (float v : vector) {
      magnitude += v * v;
    }
    magnitude = (float) Math.sqrt(magnitude);

    if (magnitude == 0) {
      return vector;
    }

    float[] normalized = new float[vector.length];
    for (int i = 0; i < vector.length; i++) {
      normalized[i] = vector[i] / magnitude;
    }

    return normalized;
  }

  /**
   * 여러 방송 정보의 임베딩을 배치로 생성합니다.
   *
   * <p>청크 단위로 분할하여 Virtual Thread에서 병렬 처리합니다.
   *
   * @param streams 방송 정보 목록
   * @return StreamEmbedding 엔티티 목록
   */
  public List<StreamEmbedding> createEmbeddingsBatch(List<AllFieldLiveDto> streams) {
    if (streams.isEmpty()) {
      return List.of();
    }

    log.info("배치 임베딩 생성 시작 - 총 {}개 방송", streams.size());

    List<List<AllFieldLiveDto>> chunks = partitionList(streams, BATCH_CHUNK_SIZE);
    log.info("{}개 청크로 분할, Virtual Thread로 병렬 처리", chunks.size());

    List<CompletableFuture<List<StreamEmbedding>>> futures =
        chunks.stream()
            .map(
                chunk -> CompletableFuture.supplyAsync(() -> processChunkSafely(chunk), aiExecutor))
            .toList();

    List<StreamEmbedding> allResults =
        futures.stream().map(CompletableFuture::join).flatMap(List::stream).toList();

    log.info("배치 임베딩 생성 완료 - {}개 결과", allResults.size());
    return allResults;
  }

  /**
   * 사용자 쿼리의 임베딩을 생성합니다.
   *
   * @param query 사용자 검색 쿼리
   * @return 임베딩 벡터
   */
  public float[] createQueryEmbedding(String query) {
    log.debug("쿼리 임베딩 생성 - query: {}", query);
    return embeddingClient.embed(query);
  }

  /** 청크를 안전하게 처리합니다. 실패 시 빈 리스트 반환. */
  private List<StreamEmbedding> processChunkSafely(List<AllFieldLiveDto> chunk) {
    try {
      List<String> texts = chunk.stream().map(this::toEmbeddingText).toList();
      List<float[]> embeddings = embeddingClient.embedBatch(texts);

      List<StreamEmbedding> results = new ArrayList<>();
      for (int i = 0; i < chunk.size(); i++) {
        AllFieldLiveDto stream = chunk.get(i);
        results.add(StreamEmbedding.create(stream.channelId(), texts.get(i), embeddings.get(i)));
      }

      log.debug("청크 처리 완료 - {}개 방송", chunk.size());
      return results;
    } catch (Exception e) {
      log.error("청크 처리 실패 ({}개 방송): {}", chunk.size(), e.getMessage());
      return List.of();
    }
  }

  /**
   * 방송 정보를 임베딩용 텍스트로 변환합니다.
   *
   * <p>제목, 채널명, 카테고리, 태그를 포함합니다. 시청자 수는 실시간 변동값이므로 제외합니다.
   *
   * @param stream 방송 정보
   * @return 임베딩할 텍스트
   */
  private String toEmbeddingText(AllFieldLiveDto stream) {
    StringBuilder sb = new StringBuilder();
    sb.append("제목: ").append(stream.liveTitle());
    sb.append(", 스트리머: ").append(stream.channelName());

    if (stream.liveCategoryValue() != null && !stream.liveCategoryValue().isBlank()) {
      sb.append(", 카테고리: ").append(stream.liveCategoryValue());
    }

    if (stream.tags() != null && !stream.tags().isEmpty()) {
      sb.append(", 태그: ").append(String.join(", ", stream.tags()));
    }

    return sb.toString();
  }

  private <T> List<List<T>> partitionList(List<T> list, int size) {
    List<List<T>> partitions = new ArrayList<>();
    for (int i = 0; i < list.size(); i += size) {
      partitions.add(list.subList(i, Math.min(i + size, list.size())));
    }
    return partitions;
  }
}
