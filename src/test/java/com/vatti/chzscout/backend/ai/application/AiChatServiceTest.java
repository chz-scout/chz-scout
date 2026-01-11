package com.vatti.chzscout.backend.ai.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.vatti.chzscout.backend.ai.domain.dto.BatchTagExtractionResult;
import com.vatti.chzscout.backend.ai.domain.dto.StreamTagResult;
import com.vatti.chzscout.backend.ai.domain.dto.UserMessageAnalysisResult;
import com.vatti.chzscout.backend.ai.infrastructure.OpenAiChatClient;
import com.vatti.chzscout.backend.ai.prompt.TagExtractionPrompts;
import com.vatti.chzscout.backend.ai.prompt.TagExtractionPrompts.StreamInput;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AiChatServiceTest {

  @Mock OpenAiChatClient openAiChatClient;
  @Spy ExecutorService aiExecutor = Executors.newVirtualThreadPerTaskExecutor();
  @InjectMocks AiChatService aiChatService;

  @AfterEach
  void tearDown() {
    aiExecutor.shutdown();
  }

  @Nested
  @DisplayName("analyzeUserMessage 메서드 테스트")
  class AnalyzeUserMessage {

    @Test
    @DisplayName("유저 메시지를 분석하여 결과를 반환한다")
    void returnsAnalysisResult() {
      // given
      String userMessage = "롤 방송 추천해줘";
      UserMessageAnalysisResult expectedResult =
          new UserMessageAnalysisResult("recommendation", List.of(), List.of("롤"), null);

      given(
              openAiChatClient.chatWithStructuredOutput(
                  eq(TagExtractionPrompts.USER_MESSAGE_ANALYSIS_SYSTEM),
                  eq(userMessage),
                  eq(UserMessageAnalysisResult.class)))
          .willReturn(expectedResult);

      // when
      UserMessageAnalysisResult result = aiChatService.analyzeUserMessage(userMessage);

      // then
      assertThat(result.getIntent()).isEqualTo("recommendation");
      assertThat(result.getKeywords()).containsExactly("롤");
      verify(openAiChatClient)
          .chatWithStructuredOutput(
              anyString(), eq(userMessage), eq(UserMessageAnalysisResult.class));
    }
  }

  @Nested
  @DisplayName("analyzeUserMessageAsync 메서드 테스트")
  class AnalyzeUserMessageAsync {

    @Test
    @DisplayName("유저 메시지를 비동기로 분석하여 CompletableFuture로 결과를 반환한다")
    void returnsCompletableFutureWithResult() throws ExecutionException, InterruptedException {
      // given
      String userMessage = "롤 방송 추천해줘";
      UserMessageAnalysisResult expectedResult =
          new UserMessageAnalysisResult("recommendation", List.of(), List.of("롤"), null);

      given(
              openAiChatClient.chatWithStructuredOutput(
                  eq(TagExtractionPrompts.USER_MESSAGE_ANALYSIS_SYSTEM),
                  eq(userMessage),
                  eq(UserMessageAnalysisResult.class)))
          .willReturn(expectedResult);

      // when
      CompletableFuture<UserMessageAnalysisResult> future =
          aiChatService.analyzeUserMessageAsync(userMessage);
      UserMessageAnalysisResult result = future.get();

      // then
      assertThat(result.getIntent()).isEqualTo("recommendation");
      assertThat(result.getKeywords()).containsExactly("롤");
    }

    @Test
    @DisplayName("AI 호출이 aiExecutor에서 실행된다")
    void executesOnAiExecutor() throws ExecutionException, InterruptedException {
      // given
      String userMessage = "안녕하세요";
      UserMessageAnalysisResult expectedResult =
          new UserMessageAnalysisResult("other", List.of(), List.of(), "안녕하세요!");

      given(
              openAiChatClient.chatWithStructuredOutput(
                  eq(TagExtractionPrompts.USER_MESSAGE_ANALYSIS_SYSTEM),
                  eq(userMessage),
                  eq(UserMessageAnalysisResult.class)))
          .willReturn(expectedResult);

      // when
      CompletableFuture<UserMessageAnalysisResult> future =
          aiChatService.analyzeUserMessageAsync(userMessage);
      future.get();

      // then
      verify(aiExecutor).execute(any(Runnable.class));
    }

    @Test
    @DisplayName("AI 서비스 예외 발생 시 CompletableFuture가 예외를 전파한다")
    void propagatesExceptionThroughFuture() {
      // given
      String userMessage = "롤 방송 추천해줘";

      given(
              openAiChatClient.chatWithStructuredOutput(
                  eq(TagExtractionPrompts.USER_MESSAGE_ANALYSIS_SYSTEM),
                  eq(userMessage),
                  eq(UserMessageAnalysisResult.class)))
          .willThrow(new RuntimeException("AI 서비스 오류"));

      // when
      CompletableFuture<UserMessageAnalysisResult> future =
          aiChatService.analyzeUserMessageAsync(userMessage);

      // then - failsWithin()으로 완료를 기다린 후 예외 검증 (CI 환경 타이밍 이슈 방지)
      assertThat(future)
          .failsWithin(java.time.Duration.ofSeconds(5))
          .withThrowableOfType(ExecutionException.class)
          .withCauseInstanceOf(RuntimeException.class)
          .withMessageContaining("AI 서비스 오류");
    }
  }

  @Nested
  @DisplayName("extractStreamTagsBatch 메서드 테스트")
  class ExtractStreamTagsBatch {

    @Test
    @DisplayName("빈 입력이면 빈 리스트를 반환하고 AI를 호출하지 않는다")
    void returnsEmptyListWhenInputEmpty() {
      // when
      List<StreamTagResult> result = aiChatService.extractStreamTagsBatch(List.of());

      // then
      assertThat(result).isEmpty();
      verify(openAiChatClient, never()).chatWithStructuredOutput(any(), any(), any());
    }

    @Test
    @DisplayName("단일 청크 크기 이하의 입력을 처리한다")
    void processesSingleChunk() {
      // given
      List<StreamInput> inputs = createStreamInputs(5);
      BatchTagExtractionResult batchResult = createBatchResult(5);

      given(
              openAiChatClient.chatWithStructuredOutput(
                  eq(TagExtractionPrompts.STREAM_TAG_EXTRACTION_BATCH_SYSTEM),
                  anyString(),
                  eq(BatchTagExtractionResult.class)))
          .willReturn(batchResult);

      // when
      List<StreamTagResult> result = aiChatService.extractStreamTagsBatch(inputs);

      // then
      assertThat(result).hasSize(5);
      verify(openAiChatClient, times(1))
          .chatWithStructuredOutput(anyString(), anyString(), eq(BatchTagExtractionResult.class));
    }

    @Test
    @DisplayName("청크 크기를 초과하는 입력을 여러 청크로 분할하여 병렬 처리한다")
    void processesMultipleChunksInParallel() {
      // given - 25개 입력 → 2개 청크 (20 + 5)
      List<StreamInput> inputs = createStreamInputs(25);
      BatchTagExtractionResult chunk1Result = createBatchResult(20);
      BatchTagExtractionResult chunk2Result = createBatchResult(5);

      given(
              openAiChatClient.chatWithStructuredOutput(
                  eq(TagExtractionPrompts.STREAM_TAG_EXTRACTION_BATCH_SYSTEM),
                  anyString(),
                  eq(BatchTagExtractionResult.class)))
          .willReturn(chunk1Result)
          .willReturn(chunk2Result);

      // when
      List<StreamTagResult> result = aiChatService.extractStreamTagsBatch(inputs);

      // then
      assertThat(result).hasSize(25);
      verify(openAiChatClient, times(2))
          .chatWithStructuredOutput(anyString(), anyString(), eq(BatchTagExtractionResult.class));
    }

    @Test
    @DisplayName("일부 청크 처리 실패 시 성공한 청크의 결과만 반환한다")
    void returnsPartialResultsWhenChunkFails() {
      // given - 25개 입력 → 2개 청크, 첫 번째만 성공
      List<StreamInput> inputs = createStreamInputs(25);
      BatchTagExtractionResult successResult = createBatchResult(20);

      given(
              openAiChatClient.chatWithStructuredOutput(
                  eq(TagExtractionPrompts.STREAM_TAG_EXTRACTION_BATCH_SYSTEM),
                  anyString(),
                  eq(BatchTagExtractionResult.class)))
          .willReturn(successResult)
          .willThrow(new RuntimeException("API 호출 실패"));

      // when
      List<StreamTagResult> result = aiChatService.extractStreamTagsBatch(inputs);

      // then - 첫 번째 청크 결과만 반환 (20개)
      assertThat(result).hasSize(20);
    }

    @Test
    @DisplayName("모든 청크 실패 시 빈 리스트를 반환한다")
    void returnsEmptyListWhenAllChunksFail() {
      // given
      List<StreamInput> inputs = createStreamInputs(5);

      given(
              openAiChatClient.chatWithStructuredOutput(
                  eq(TagExtractionPrompts.STREAM_TAG_EXTRACTION_BATCH_SYSTEM),
                  anyString(),
                  eq(BatchTagExtractionResult.class)))
          .willThrow(new RuntimeException("API 호출 실패"));

      // when
      List<StreamTagResult> result = aiChatService.extractStreamTagsBatch(inputs);

      // then
      assertThat(result).isEmpty();
    }

    private List<StreamInput> createStreamInputs(int count) {
      List<StreamInput> inputs = new ArrayList<>();
      for (int i = 0; i < count; i++) {
        inputs.add(new StreamInput("channel_" + i, "테스트 방송 " + i, "게임", List.of("태그" + i)));
      }
      return inputs;
    }

    private BatchTagExtractionResult createBatchResult(int count) {
      List<StreamTagResult> results = new ArrayList<>();
      for (int i = 0; i < count; i++) {
        results.add(
            new StreamTagResult(
                "channel_" + i, List.of("게임", "태그" + i), List.of("게임", "태그" + i, "의미태그"), 0.9));
      }
      return new BatchTagExtractionResult(results);
    }
  }
}
