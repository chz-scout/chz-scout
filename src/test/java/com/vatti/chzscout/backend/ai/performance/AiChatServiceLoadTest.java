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
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
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

  @Test
  @DisplayName("비동기 방식 Burst: 100개 요청 동시 발생 (Virtual Thread)")
  void measureBurstPerformanceAsync() {
    // given
    stubWithDelay(BURST_DELAY_MS);
    List<CompletableFuture<Long>> futures = new ArrayList<>();

    // when
    long startTime = System.currentTimeMillis();

    for (int i = 0; i < BURST_REQUESTS; i++) {
      final int requestId = i;
      futures.add(measureSingleRequestAsync(requestId));
    }

    List<Long> responseTimes = futures.stream().map(CompletableFuture::join).toList();
    long totalTime = System.currentTimeMillis() - startTime;

    // then
    printBurstReportAsync(responseTimes, totalTime);
    assertThat(responseTimes).hasSize(BURST_REQUESTS);
  }

  @Test
  @DisplayName("스레드 해방 시간 비교: 동기 vs 비동기")
  void compareThreadReleaseTime() {
    // given
    stubWithDelay(BURST_DELAY_MS);
    int requestCount = 10;

    // when - 동기 방식: 호출 스레드가 블로킹되는 시간 측정
    List<Long> syncBlockingTimes = new ArrayList<>();
    for (int i = 0; i < requestCount; i++) {
      long start = System.nanoTime();
      aiChatService.analyzeUserMessage("테스트 #" + i);
      long blocked = (System.nanoTime() - start) / 1_000_000; // ms
      syncBlockingTimes.add(blocked);
    }

    // when - 비동기 방식: 호출 스레드가 블로킹되는 시간 측정
    List<Long> asyncBlockingTimes = new ArrayList<>();
    List<CompletableFuture<?>> futures = new ArrayList<>();
    for (int i = 0; i < requestCount; i++) {
      long start = System.nanoTime();
      CompletableFuture<?> future = aiChatService.analyzeUserMessageAsync("테스트 #" + i);
      long blocked = (System.nanoTime() - start) / 1_000_000; // ms
      asyncBlockingTimes.add(blocked);
      futures.add(future);
    }

    // 비동기 작업 완료 대기
    futures.forEach(CompletableFuture::join);

    // then
    printThreadReleaseTimeReport(syncBlockingTimes, asyncBlockingTimes);

    // 비동기는 동기보다 빠르게 스레드를 해방해야 함 (상대적 비교로 CI 환경 안정성 확보)
    double asyncAvg = asyncBlockingTimes.stream().mapToLong(Long::longValue).average().orElse(0);
    double syncAvg = syncBlockingTimes.stream().mapToLong(Long::longValue).average().orElse(0);
    assertThat(asyncAvg).isLessThan(syncAvg);
  }

  @Test
  @DisplayName("동시 요청 시 OS 스레드(Carrier Thread) 사용량 비교")
  void compareThreadUsage() throws InterruptedException {
    // given
    stubWithDelay(BURST_DELAY_MS);
    int requestCount = 100;

    // JVM 기본 스레드 수 측정 (테스트 시작 전)
    int baselineThreadCount = Thread.activeCount();

    // ========== 동기 방식: Platform Thread Pool ==========
    ExecutorService platformExecutor = Executors.newFixedThreadPool(requestCount);
    // 스레드별 총 사용 시간 기록
    ConcurrentHashMap<String, Long> platformThreadUsageTime = new ConcurrentHashMap<>();

    long syncStart = System.currentTimeMillis();

    // 작업 실행 중 최대 스레드 수 측정 (AtomicInteger로 동시성 안전하게)
    AtomicInteger maxPlatformThreads = new AtomicInteger(0);
    List<CompletableFuture<Void>> syncFutures = new ArrayList<>();
    for (int i = 0; i < requestCount; i++) {
      final int id = i;
      syncFutures.add(
          CompletableFuture.runAsync(
              () -> {
                String threadName = Thread.currentThread().getName();
                long taskStart = System.currentTimeMillis();

                maxPlatformThreads.updateAndGet(current -> Math.max(current, Thread.activeCount()));
                aiChatService.analyzeUserMessage("동기 #" + id);

                long taskDuration = System.currentTimeMillis() - taskStart;
                platformThreadUsageTime.merge(threadName, taskDuration, Long::sum);
              },
              platformExecutor));
    }
    syncFutures.forEach(CompletableFuture::join);
    long syncTime = System.currentTimeMillis() - syncStart;
    platformExecutor.shutdown();

    int platformThreadsUsed = maxPlatformThreads.get() - baselineThreadCount;

    // ========== 비동기 방식: Virtual Thread ==========
    Thread.sleep(100);
    int asyncBaselineThreadCount = Thread.activeCount();

    // Virtual Thread별 사용 시간 기록 (AtomicInteger로 동시성 안전하게)
    ConcurrentHashMap<String, Long> virtualThreadUsageTime = new ConcurrentHashMap<>();
    AtomicInteger maxVirtualCarrierThreads = new AtomicInteger(0);

    long asyncStart = System.currentTimeMillis();
    List<CompletableFuture<Void>> asyncFutures = new ArrayList<>();
    for (int i = 0; i < requestCount; i++) {
      final int id = i;
      final long taskStart = System.currentTimeMillis();
      asyncFutures.add(
          aiChatService
              .analyzeUserMessageAsync("비동기 #" + id)
              .thenRun(
                  () -> {
                    Thread current = Thread.currentThread();
                    long taskDuration = System.currentTimeMillis() - taskStart;
                    virtualThreadUsageTime.merge(current.getName(), taskDuration, Long::sum);

                    if (!current.isVirtual()) {
                      maxVirtualCarrierThreads.updateAndGet(
                          curr -> Math.max(curr, Thread.activeCount()));
                    }
                  }));
    }
    asyncFutures.forEach(CompletableFuture::join);
    long asyncTime = System.currentTimeMillis() - asyncStart;

    int carrierThreadsUsed = maxVirtualCarrierThreads.get() - asyncBaselineThreadCount;
    int estimatedCarrierThreads =
        carrierThreadsUsed > 0 ? carrierThreadsUsed : Runtime.getRuntime().availableProcessors();

    // then
    printThreadUsageTimeReport(
        platformThreadUsageTime,
        syncTime,
        virtualThreadUsageTime,
        asyncTime,
        requestCount,
        estimatedCarrierThreads);
  }

  @Test
  @DisplayName("비동기 방식 Sustained: 초당 5회 × 10초 지속 부하 (Virtual Thread)")
  void measureSustainedPerformanceAsync() throws InterruptedException {
    // given
    stubWithDelay(SUSTAINED_DELAY_MS);
    List<CompletableFuture<Long>> futures = new ArrayList<>();
    int totalRequests = REQUESTS_PER_SECOND * TEST_DURATION_SECONDS;

    // when
    long startTime = System.currentTimeMillis();

    for (int second = 0; second < TEST_DURATION_SECONDS; second++) {
      long secondStart = System.currentTimeMillis();

      for (int i = 0; i < REQUESTS_PER_SECOND; i++) {
        final int requestId = second * REQUESTS_PER_SECOND + i;
        futures.add(measureSingleRequestAsync(requestId));
      }

      long elapsed = System.currentTimeMillis() - secondStart;
      if (elapsed < 1000) {
        TimeUnit.MILLISECONDS.sleep(1000 - elapsed);
      }
    }

    List<Long> responseTimes = futures.stream().map(CompletableFuture::join).toList();
    long totalTime = System.currentTimeMillis() - startTime;

    // then
    printSustainedReportAsync(responseTimes, totalTime, totalRequests);
    assertThat(responseTimes).hasSize(totalRequests);
  }

  // ==================== 헬퍼 메서드 ====================

  /** 단일 요청 수행 및 응답 시간 측정 (동기) */
  private long measureSingleRequest(int requestId) {
    long requestStart = System.currentTimeMillis();
    UserMessageAnalysisResult result = aiChatService.analyzeUserMessage("롤 방송 추천해줘 #" + requestId);
    long requestEnd = System.currentTimeMillis();

    assertThat(result).isNotNull();
    assertThat(result.getIntent()).isEqualTo("recommendation");

    return requestEnd - requestStart;
  }

  /** 단일 요청 수행 및 응답 시간 측정 (비동기 - Virtual Thread) */
  private CompletableFuture<Long> measureSingleRequestAsync(int requestId) {
    long requestStart = System.currentTimeMillis();

    return aiChatService
        .analyzeUserMessageAsync("롤 방송 추천해줘 #" + requestId)
        .thenApply(
            result -> {
              long requestEnd = System.currentTimeMillis();
              assertThat(result).isNotNull();
              assertThat(result.getIntent()).isEqualTo("recommendation");
              return requestEnd - requestStart;
            });
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

  /** Burst 테스트 결과 출력 (비동기) */
  private void printBurstReportAsync(List<Long> responseTimes, long totalTime) {
    LongSummaryStatistics stats =
        responseTimes.stream().mapToLong(Long::longValue).summaryStatistics();

    System.out.println();
    System.out.println("========== Burst 테스트 결과 (비동기/Virtual Thread) ==========");
    System.out.println("동시 요청 수: " + BURST_REQUESTS);
    System.out.println("API 지연 시간: " + BURST_DELAY_MS + "ms");
    System.out.println("총 소요 시간: " + totalTime + "ms");
    System.out.printf("평균 응답 시간: %.2fms%n", stats.getAverage());
    System.out.println("최소 응답 시간: " + stats.getMin() + "ms");
    System.out.println("최대 응답 시간: " + stats.getMax() + "ms");
    System.out.printf("처리량 (TPS): %.2f%n", BURST_REQUESTS * 1000.0 / totalTime);
    System.out.println("=".repeat(60));
    System.out.println();
  }

  /** Sustained Load 테스트 결과 출력 (비동기) */
  private void printSustainedReportAsync(
      List<Long> responseTimes, long totalTime, int totalRequests) {
    LongSummaryStatistics stats =
        responseTimes.stream().mapToLong(Long::longValue).summaryStatistics();

    System.out.println();
    System.out.println("========== Sustained Load 테스트 결과 (비동기/Virtual Thread) ==========");
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
    System.out.println("=".repeat(65));
    System.out.println();
  }

  /** 스레드 해방 시간 비교 리포트 출력 */
  private void printThreadReleaseTimeReport(List<Long> syncTimes, List<Long> asyncTimes) {
    LongSummaryStatistics syncStats =
        syncTimes.stream().mapToLong(Long::longValue).summaryStatistics();
    LongSummaryStatistics asyncStats =
        asyncTimes.stream().mapToLong(Long::longValue).summaryStatistics();

    System.out.println();
    System.out.println("========== 스레드 해방 시간 비교 ==========");
    System.out.println();
    System.out.println("[ 동기 방식 - 호출 스레드 블로킹 시간 ]");
    System.out.printf("  평균: %.2fms%n", syncStats.getAverage());
    System.out.println("  최소: " + syncStats.getMin() + "ms");
    System.out.println("  최대: " + syncStats.getMax() + "ms");
    System.out.println();
    System.out.println("[ 비동기 방식 - 호출 스레드 블로킹 시간 ]");
    System.out.printf("  평균: %.2fms%n", asyncStats.getAverage());
    System.out.println("  최소: " + asyncStats.getMin() + "ms");
    System.out.println("  최대: " + asyncStats.getMax() + "ms");
    System.out.println();
    System.out.printf(
        "⚡ 비동기가 %.1f배 빠르게 스레드 해방%n", syncStats.getAverage() / asyncStats.getAverage());
    System.out.println("=".repeat(45));
    System.out.println();
  }

  /** 스레드별 사용 시간 비교 리포트 출력 */
  private void printThreadUsageTimeReport(
      ConcurrentHashMap<String, Long> platformUsage,
      long syncTime,
      ConcurrentHashMap<String, Long> virtualUsage,
      long asyncTime,
      int requestCount,
      int estimatedCarrierThreads) {

    // 동기 방식 통계
    long platformTotalTime = platformUsage.values().stream().mapToLong(Long::longValue).sum();
    double platformAvgPerThread =
        platformUsage.isEmpty() ? 0 : (double) platformTotalTime / platformUsage.size();
    int platformTasksPerThread = platformUsage.isEmpty() ? 0 : requestCount / platformUsage.size();

    // 비동기 방식 통계
    long virtualTotalTime = virtualUsage.values().stream().mapToLong(Long::longValue).sum();
    double virtualAvgPerThread =
        virtualUsage.isEmpty() ? 0 : (double) virtualTotalTime / virtualUsage.size();

    System.out.println();
    System.out.println("========== 스레드별 사용 시간 비교 ==========");
    System.out.println("총 요청 수: " + requestCount);
    System.out.println("API 지연 시간: " + BURST_DELAY_MS + "ms");
    System.out.println("CPU 코어 수: " + Runtime.getRuntime().availableProcessors());
    System.out.println();

    System.out.println("[ 동기 방식 - Platform Thread Pool ]");
    System.out.println("  스레드 수: " + platformUsage.size() + "개");
    System.out.println("  스레드당 처리 작업: " + platformTasksPerThread + "개");
    System.out.printf("  스레드당 평균 사용 시간: %.0fms%n", platformAvgPerThread);
    System.out.printf("  전체 스레드 사용 시간 합계: %.1f초%n", platformTotalTime / 1000.0);
    System.out.println("  총 소요 시간: " + syncTime + "ms");
    System.out.println();

    // 동기 방식 Top 5 스레드
    System.out.println("  [Top 5 사용 시간]");
    platformUsage.entrySet().stream()
        .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
        .limit(5)
        .forEach(e -> System.out.printf("    %s: %dms%n", e.getKey(), e.getValue()));
    System.out.println();

    System.out.println("[ 비동기 방식 - Virtual Thread ]");
    System.out.println("  측정된 스레드 수: " + virtualUsage.size() + "개 (콜백 실행 스레드)");
    System.out.println("  Carrier Thread (추정): ~" + estimatedCarrierThreads + "개");
    System.out.printf("  전체 사용 시간 합계: %.1f초%n", virtualTotalTime / 1000.0);
    System.out.println("  총 소요 시간: " + asyncTime + "ms");
    System.out.println();

    // 비동기 방식 스레드별 시간 (있으면)
    if (!virtualUsage.isEmpty()) {
      System.out.println("  [스레드별 사용 시간]");
      virtualUsage.entrySet().stream()
          .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
          .limit(5)
          .forEach(
              e -> {
                String name = e.getKey().isEmpty() ? "(unnamed)" : e.getKey();
                System.out.printf("    %s: %dms%n", name, e.getValue());
              });
      System.out.println();
    }

    // 스레드 효율성 비교
    System.out.println("[ 스레드 효율성 비교 ]");

    // Carrier Thread 관점에서 스레드 시간 계산
    // 동기: 각 스레드가 실제로 사용한 시간의 합
    // 비동기: Carrier Thread 수 × 총 소요 시간 (최대 사용 시간)
    long carrierTotalTime = (long) estimatedCarrierThreads * asyncTime;

    System.out.printf(
        "  동기 - 총 스레드 시간: %.1f초 (%d개 스레드 × 평균 %.1f초)%n",
        platformTotalTime / 1000.0, platformUsage.size(), platformAvgPerThread / 1000.0);
    System.out.printf(
        "  비동기 - 총 Carrier 시간: %.1f초 (%d개 Carrier × %.1f초)%n",
        carrierTotalTime / 1000.0, estimatedCarrierThreads, asyncTime / 1000.0);
    System.out.println();

    if (platformTotalTime > carrierTotalTime) {
      double savedTime = platformTotalTime - carrierTotalTime;
      double savedPercent = (1 - (double) carrierTotalTime / platformTotalTime) * 100;
      System.out.printf("  ⚡ 스레드 시간 %.1f초 절약 (%.1f%% 감소)%n", savedTime / 1000.0, savedPercent);
    }

    if (platformUsage.size() > estimatedCarrierThreads) {
      System.out.printf(
          "  ⚡ OS 스레드 %d개 절약 (%.1f%% 감소)%n",
          platformUsage.size() - estimatedCarrierThreads,
          (1 - (double) estimatedCarrierThreads / platformUsage.size()) * 100);
    }

    // Carrier Thread 활용률
    double carrierUtilization = (double) requestCount / estimatedCarrierThreads;
    System.out.printf(
        "  🔄 Carrier 활용률: %.1f배 (1개 Carrier가 평균 %.1f개 Virtual Thread 처리)%n",
        carrierUtilization, carrierUtilization);

    System.out.println("=".repeat(50));
    System.out.println();
  }
}
