package com.vatti.chzscout.backend.ai.performance;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.vatti.chzscout.backend.ai.application.AiChatService;
import com.vatti.chzscout.backend.ai.domain.dto.UserMessageAnalysisResult;
import java.util.ArrayList;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;

/**
 * AI Chat Service 부하 테스트.
 *
 * <p>WireMock을 사용하여 OpenAI API 응답을 시뮬레이션하고, 동시 요청 시 성능을 측정합니다.
 */
@Tag("load-test")
@SpringBootTest(properties = "openai.api.base-url=http://localhost:19999")
@ActiveProfiles("test")
class AiChatServiceLoadTest {

  private static final int WIREMOCK_PORT = 19999;

  @RegisterExtension
  static WireMockExtension wireMock =
      WireMockExtension.newInstance().options(wireMockConfig().port(WIREMOCK_PORT)).build();

  @Autowired private AiChatService aiChatService;

  // ==================== Burst 테스트 설정 ====================

  /** API 응답 지연 시간 (ms) - Burst 테스트용 */
  private static final int BURST_DELAY_MS = 2000;

  /** 동시 요청 수 - Burst 테스트용 */
  private static final int BURST_REQUESTS = 100;

  // ==================== Sustained Load 테스트 설정 ====================

  /** API 응답 지연 시간 (ms) */
  private static final int SUSTAINED_DELAY_MS = 2000;

  /** 초당 요청 수 (OkHttp 기본 maxRequestsPerHost=5 기준) */
  private static final int REQUESTS_PER_SECOND = 5;

  /** 테스트 지속 시간 (초) */
  private static final int TEST_DURATION_SECONDS = 10;

  /** 최대 동시 요청 수 = 초당 요청 × 지연 시간(초) = 10 */
  private static final int MAX_CONCURRENT = REQUESTS_PER_SECOND * (SUSTAINED_DELAY_MS / 1000);

  @BeforeEach
  void setUp() {
    stubOpenAiChatCompletion();
  }

  @Test
  @DisplayName("동기 방식 Burst: 100개 요청 동시 발생")
  void measureBurstPerformance() {
    // given
    stubWithDelay(BURST_DELAY_MS);
    ExecutorService executor = Executors.newFixedThreadPool(BURST_REQUESTS);
    List<CompletableFuture<Long>> futures = new ArrayList<>();

    // when
    long startTime = System.currentTimeMillis();

    for (int i = 0; i < BURST_REQUESTS; i++) {
      final int requestId = i;
      futures.add(CompletableFuture.supplyAsync(() -> measureSingleRequest(requestId), executor));
    }

    List<Long> responseTimes = futures.stream().map(CompletableFuture::join).toList();
    long totalTime = System.currentTimeMillis() - startTime;
    executor.shutdown();

    // then
    printBurstReport(responseTimes, totalTime);
    assertThat(responseTimes).hasSize(BURST_REQUESTS);
  }

  @Test
  @DisplayName("동기 방식 Sustained: 초당 5회 × 10초 지속 부하")
  void measureSustainedPerformance() throws InterruptedException {
    // given
    stubWithDelay(SUSTAINED_DELAY_MS);
    ExecutorService executor = Executors.newFixedThreadPool(MAX_CONCURRENT);
    List<CompletableFuture<Long>> futures = new ArrayList<>();
    int totalRequests = REQUESTS_PER_SECOND * TEST_DURATION_SECONDS;

    // when
    long startTime = System.currentTimeMillis();

    for (int second = 0; second < TEST_DURATION_SECONDS; second++) {
      long secondStart = System.currentTimeMillis();

      // 해당 초에 REQUESTS_PER_SECOND개 요청 발생
      for (int i = 0; i < REQUESTS_PER_SECOND; i++) {
        final int requestId = second * REQUESTS_PER_SECOND + i;
        futures.add(CompletableFuture.supplyAsync(() -> measureSingleRequest(requestId), executor));
      }

      // 다음 초까지 대기 (정확한 초당 요청 수 유지)
      long elapsed = System.currentTimeMillis() - secondStart;
      if (elapsed < 1000) {
        TimeUnit.MILLISECONDS.sleep(1000 - elapsed);
      }
    }

    List<Long> responseTimes = futures.stream().map(CompletableFuture::join).toList();
    long totalTime = System.currentTimeMillis() - startTime;
    executor.shutdown();

    // then
    printSustainedReport(responseTimes, totalTime, totalRequests);
    assertThat(responseTimes).hasSize(totalRequests);
  }

  // ==================== 헬퍼 메서드 ====================

  /** 단일 요청 수행 및 응답 시간 측정 */
  private long measureSingleRequest(int requestId) {
    long requestStart = System.currentTimeMillis();
    UserMessageAnalysisResult result = aiChatService.analyzeUserMessage("롤 방송 추천해줘 #" + requestId);
    long requestEnd = System.currentTimeMillis();

    assertThat(result).isNotNull();
    assertThat(result.getIntent()).isEqualTo("recommendation");

    return requestEnd - requestStart;
  }

  /** OpenAI Chat Completion API stub 설정 (기본 지연) */
  private void stubOpenAiChatCompletion() {
    stubWithDelay(BURST_DELAY_MS);
  }

  /** OpenAI Chat Completion API stub 설정 (지연 시간 지정) */
  private void stubWithDelay(int delayMs) {
    wireMock.resetAll();
    String mockResponse = OpenAiResponseFixture.recommendationResponse();

    wireMock.stubFor(
        post(urlPathEqualTo("/chat/completions"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .withFixedDelay(delayMs)
                    .withBody(mockResponse)));
  }

  // ==================== 리포트 출력 ====================

  /** Burst 테스트 결과 출력 */
  private void printBurstReport(List<Long> responseTimes, long totalTime) {
    LongSummaryStatistics stats =
        responseTimes.stream().mapToLong(Long::longValue).summaryStatistics();

    System.out.println();
    System.out.println("========== Burst 테스트 결과 ==========");
    System.out.println("동시 요청 수: " + BURST_REQUESTS);
    System.out.println("API 지연 시간: " + BURST_DELAY_MS + "ms");
    System.out.println("총 소요 시간: " + totalTime + "ms");
    System.out.printf("평균 응답 시간: %.2fms%n", stats.getAverage());
    System.out.println("최소 응답 시간: " + stats.getMin() + "ms");
    System.out.println("최대 응답 시간: " + stats.getMax() + "ms");
    System.out.printf("처리량 (TPS): %.2f%n", BURST_REQUESTS * 1000.0 / totalTime);
    System.out.println("=".repeat(45));
    System.out.println();
  }

  /** Sustained Load 테스트 결과 출력 */
  private void printSustainedReport(List<Long> responseTimes, long totalTime, int totalRequests) {
    LongSummaryStatistics stats =
        responseTimes.stream().mapToLong(Long::longValue).summaryStatistics();

    System.out.println();
    System.out.println("========== Sustained Load 테스트 결과 ==========");
    System.out.println("초당 요청 수: " + REQUESTS_PER_SECOND);
    System.out.println("테스트 지속 시간: " + TEST_DURATION_SECONDS + "초");
    System.out.println("총 요청 수: " + totalRequests);
    System.out.println("최대 동시 요청: " + MAX_CONCURRENT);
    System.out.println("API 지연 시간: " + SUSTAINED_DELAY_MS + "ms");
    System.out.println("총 소요 시간: " + totalTime + "ms");
    System.out.printf("평균 응답 시간: %.2fms%n", stats.getAverage());
    System.out.println("최소 응답 시간: " + stats.getMin() + "ms");
    System.out.println("최대 응답 시간: " + stats.getMax() + "ms");
    System.out.printf("처리량 (TPS): %.2f%n", totalRequests * 1000.0 / totalTime);
    System.out.println("=".repeat(50));
    System.out.println();
  }
}
