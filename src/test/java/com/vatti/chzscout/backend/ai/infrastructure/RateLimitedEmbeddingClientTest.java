package com.vatti.chzscout.backend.ai.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.openai.errors.RateLimitException;
import com.vatti.chzscout.backend.ai.config.AiRateLimitProperties;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("RateLimitedEmbeddingClient 테스트")
class RateLimitedEmbeddingClientTest {

  @Mock private OpenAiEmbeddingClientImpl delegate;

  private AiRateLimitProperties properties;
  private RateLimitedEmbeddingClient client;

  @BeforeEach
  void setUp() {
    properties = new AiRateLimitProperties();
    properties.setPermits(5);
    properties.setMaxRetries(3);
    properties.setInitialBackoffMs(10); // 테스트용 짧은 대기 시간

    client = new RateLimitedEmbeddingClient(delegate, properties);
    client.init();
  }

  @Nested
  @DisplayName("embed 메서드")
  class EmbedTest {

    @Test
    @DisplayName("정상 호출 시 delegate에 위임한다")
    void embed_success() {
      // given
      float[] expected = new float[] {0.1f, 0.2f, 0.3f};
      when(delegate.embed("test")).thenReturn(expected);

      // when
      float[] result = client.embed("test");

      // then
      assertThat(result).isEqualTo(expected);
      verify(delegate, times(1)).embed("test");
    }

    @Test
    @DisplayName("RateLimitException 발생 시 재시도 후 성공한다")
    void embed_retryOnRateLimitException() {
      // given
      float[] expected = new float[] {0.1f, 0.2f, 0.3f};
      RateLimitException rateLimitError = mock(RateLimitException.class);

      when(delegate.embed("test"))
          .thenThrow(rateLimitError) // 1차 실패
          .thenThrow(rateLimitError) // 2차 실패
          .thenReturn(expected); // 3차 성공

      // when
      float[] result = client.embed("test");

      // then
      assertThat(result).isEqualTo(expected);
      verify(delegate, times(3)).embed("test");
    }

    @Test
    @DisplayName("최대 재시도 횟수 초과 시 예외를 던진다")
    void embed_throwsAfterMaxRetries() {
      // given
      RateLimitException rateLimitError = mock(RateLimitException.class);
      when(delegate.embed("test")).thenThrow(rateLimitError);

      // when & then
      assertThatThrownBy(() -> client.embed("test")).isInstanceOf(RateLimitException.class);

      // 초기 시도 1회 + 재시도 3회 = 총 4회
      verify(delegate, times(4)).embed("test");
    }
  }

  @Nested
  @DisplayName("embedBatch 메서드")
  class EmbedBatchTest {

    @Test
    @DisplayName("정상 호출 시 delegate에 위임한다")
    void embedBatch_success() {
      // given
      List<float[]> expected = List.of(new float[] {0.1f}, new float[] {0.2f});
      when(delegate.embedBatch(anyList())).thenReturn(expected);

      // when
      List<float[]> result = client.embedBatch(List.of("text1", "text2"));

      // then
      assertThat(result).isEqualTo(expected);
      verify(delegate, times(1)).embedBatch(anyList());
    }
  }

  @Nested
  @DisplayName("Semaphore 동시성 제어")
  class SemaphoreTest {

    @Test
    @DisplayName("동시 요청 수가 permits를 초과하지 않는다")
    void semaphore_limitsConcurrentRequests() throws InterruptedException {
      // given
      int totalRequests = 20;
      AtomicInteger concurrentCount = new AtomicInteger(0);
      AtomicInteger maxConcurrent = new AtomicInteger(0);
      CountDownLatch startLatch = new CountDownLatch(1);
      CountDownLatch doneLatch = new CountDownLatch(totalRequests);

      when(delegate.embed(anyString()))
          .thenAnswer(
              invocation -> {
                int current = concurrentCount.incrementAndGet();
                maxConcurrent.updateAndGet(max -> Math.max(max, current));

                Thread.sleep(50); // API 호출 시뮬레이션

                concurrentCount.decrementAndGet();
                return new float[] {0.1f};
              });

      ExecutorService executor = Executors.newFixedThreadPool(totalRequests);

      // when
      for (int i = 0; i < totalRequests; i++) {
        final int index = i;
        executor.submit(
            () -> {
              try {
                startLatch.await();
                client.embed("test" + index);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              } finally {
                doneLatch.countDown();
              }
            });
      }

      startLatch.countDown(); // 모든 스레드 동시 시작
      doneLatch.await(); // 모든 요청 완료 대기
      executor.shutdown();

      // then
      assertThat(maxConcurrent.get()).isLessThanOrEqualTo(properties.getPermits());
    }
  }
}
