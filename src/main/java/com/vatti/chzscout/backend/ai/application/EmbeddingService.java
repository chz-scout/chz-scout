package com.vatti.chzscout.backend.ai.application;

import com.vatti.chzscout.backend.ai.domain.entity.StreamEmbedding;
import com.vatti.chzscout.backend.ai.infrastructure.EmbeddingClient;
import com.vatti.chzscout.backend.stream.domain.AllFieldLiveDto;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

  /** 배치 처리 시 한 번에 처리할 방송 수. OpenAI API 제한 고려 (최대 2048개). */
  private static final int BATCH_CHUNK_SIZE = 100;

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
