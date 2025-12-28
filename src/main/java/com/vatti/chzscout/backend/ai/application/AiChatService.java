package com.vatti.chzscout.backend.ai.application;

import com.vatti.chzscout.backend.ai.domain.dto.BatchTagExtractionResult;
import com.vatti.chzscout.backend.ai.domain.dto.StreamTagResult;
import com.vatti.chzscout.backend.ai.domain.dto.UserMessageAnalysisResult;
import com.vatti.chzscout.backend.ai.infrastructure.OpenAiChatClient;
import com.vatti.chzscout.backend.ai.prompt.TagExtractionPrompts;
import com.vatti.chzscout.backend.ai.prompt.TagExtractionPrompts.StreamInput;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * AI 채팅 서비스.
 *
 * <p>OpenAI Chat Completion API를 호출하여 AI 응답을 받아옵니다.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AiChatService {

  private final OpenAiChatClient openAiChatClient;

  /**
   * 유저 메시지를 분석하여 의도와 태그를 추출합니다.
   *
   * <p>Structured Output을 사용하여 의도 분류, 태그 추출, 응답 생성을 한 번에 처리합니다.
   *
   * @param userMessage 사용자가 보낸 메시지
   * @return 분석 결과 (intent, tags, reply)
   */
  public UserMessageAnalysisResult analyzeUserMessage(String userMessage) {
    log.debug("유저 메시지 분석 - message: {}", userMessage);

    return openAiChatClient.chatWithStructuredOutput(
        TagExtractionPrompts.USER_MESSAGE_ANALYSIS_SYSTEM,
        userMessage,
        UserMessageAnalysisResult.class);
  }

  /**
   * 여러 방송 데이터에서 태그를 배치로 추출합니다.
   *
   * <p>최대 10개의 청크를 병렬로 처리하여 성능을 최적화합니다.
   *
   * @param streams 방송 입력 리스트
   * @return 각 방송별 태그 추출 결과 리스트
   */
  public List<StreamTagResult> extractStreamTagsBatch(List<StreamInput> streams) {
    if (streams.isEmpty()) {
      return List.of();
    }

    log.info("배치 태그 추출 시작 - 총 {}개 방송", streams.size());

    List<List<StreamInput>> chunks = partitionList(streams, BATCH_CHUNK_SIZE);
    log.info("{}개 청크로 분할, 최대 {}개 병렬 처리", chunks.size(), MAX_PARALLEL_REQUESTS);

    // 최대 10개 스레드로 병렬 처리
    ExecutorService executor = Executors.newFixedThreadPool(MAX_PARALLEL_REQUESTS);

    try {
      // 모든 청크를 병렬로 처리
      List<CompletableFuture<List<StreamTagResult>>> futures =
          chunks.stream()
              .map(
                  chunk -> CompletableFuture.supplyAsync(() -> processChunkSafely(chunk), executor))
              .toList();

      // 모든 결과 수집
      List<StreamTagResult> allResults =
          futures.stream().map(CompletableFuture::join).flatMap(List::stream).toList();

      log.info("배치 태그 추출 완료 - {}개 결과", allResults.size());
      return allResults;
    } finally {
      executor.shutdown();
    }
  }

  /** 청크를 안전하게 처리합니다. 실패 시 빈 리스트 반환. */
  private List<StreamTagResult> processChunkSafely(List<StreamInput> chunk) {
    try {
      BatchTagExtractionResult batchResult = extractBatchChunk(chunk);
      log.debug("청크 처리 완료 - {}개 방송", chunk.size());
      return batchResult.results();
    } catch (Exception e) {
      log.error("청크 처리 실패 ({}개 방송): {}", chunk.size(), e.getMessage());
      return List.of();
    }
  }

  private BatchTagExtractionResult extractBatchChunk(List<StreamInput> chunk) {
    String userPrompt = TagExtractionPrompts.formatStreamTagExtractionBatchUser(chunk);

    return openAiChatClient.chatWithStructuredOutput(
        TagExtractionPrompts.STREAM_TAG_EXTRACTION_BATCH_SYSTEM,
        userPrompt,
        BatchTagExtractionResult.class);
  }

  private <T> List<List<T>> partitionList(List<T> list, int size) {
    List<List<T>> partitions = new ArrayList<>();
    for (int i = 0; i < list.size(); i += size) {
      partitions.add(list.subList(i, Math.min(i + size, list.size())));
    }
    return partitions;
  }

  /** 배치 처리 시 한 번에 처리할 방송 수. 토큰 제한 고려. */
  private static final int BATCH_CHUNK_SIZE = 20;

  /** 최대 동시 API 요청 수. OpenAI Rate Limit 고려. */
  private static final int MAX_PARALLEL_REQUESTS = 10;
}
