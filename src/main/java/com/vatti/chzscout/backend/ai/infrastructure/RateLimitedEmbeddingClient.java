package com.vatti.chzscout.backend.ai.infrastructure;

import com.openai.errors.RateLimitException;
import com.vatti.chzscout.backend.ai.config.AiRateLimitProperties;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Rate Limit이 적용된 EmbeddingClient Decorator.
 *
 * <p>Semaphore로 동시 요청 수를 제한하고, 429 에러 시 Exponential Backoff로 재시도합니다.
 */
@Slf4j
@Primary
@Component
public class RateLimitedEmbeddingClient implements EmbeddingClient {

  private final OpenAiEmbeddingClientImpl delegate;
  private final AiRateLimitProperties properties;
  private Semaphore semaphore;

  public RateLimitedEmbeddingClient(
      OpenAiEmbeddingClientImpl delegate, AiRateLimitProperties properties) {
    this.delegate = delegate;
    this.properties = properties;
  }

  @PostConstruct
  void init() {
    this.semaphore = new Semaphore(properties.getPermits());
    log.info(
        "RateLimitedEmbeddingClient 초기화 - permits: {}, maxRetries: {}, initialBackoffMs: {}",
        properties.getPermits(),
        properties.getMaxRetries(),
        properties.getInitialBackoffMs());
  }

  @Override
  public float[] embed(String text) {
    return executeWithRateLimit(() -> delegate.embed(text));
  }

  @Override
  public List<float[]> embedBatch(List<String> texts) {
    return executeWithRateLimit(() -> delegate.embedBatch(texts));
  }

  private <T> T executeWithRateLimit(Supplier<T> action) {
    try {
      semaphore.acquire();
      log.debug("Semaphore 획득 - 남은 permits: {}", semaphore.availablePermits());

      return executeWithRetry(action);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Rate limit 대기 중 인터럽트 발생", e);
    } finally {
      semaphore.release();
      log.debug("Semaphore 반환 - 남은 permits: {}", semaphore.availablePermits());
    }
  }

  /**
   * Exponential Backoff로 재시도를 수행합니다.
   *
   * <p>RateLimitException 발생 시 지수적으로 증가하는 대기 시간 후 재시도합니다. Jitter를 추가하여 thundering herd 문제를 방지합니다.
   *
   * @param action 실행할 작업
   * @return 작업 결과
   * @throws RateLimitException 최대 재시도 횟수 초과 시
   */
  private <T> T executeWithRetry(Supplier<T> action) {
    int maxRetries = properties.getMaxRetries();
    long backoffMs = properties.getInitialBackoffMs();
    RateLimitException lastError = null;

    for (int attempt = 0; attempt <= maxRetries; attempt++) {
      try {
        return action.get();
      } catch (RateLimitException e) {
        lastError = e;

        if (attempt == maxRetries) {
          log.error("Rate limit 최대 재시도 횟수 초과 - attempts: {}", attempt + 1);
          break;
        }

        // Jitter 추가 (0~500ms 랜덤)
        long jitter = ThreadLocalRandom.current().nextLong(0, 500);
        long sleepTime = backoffMs + jitter;

        log.warn(
            "Rate limit 발생, 재시도 예정 - attempt: {}/{}, backoff: {}ms",
            attempt + 1,
            maxRetries,
            sleepTime);

        try {
          Thread.sleep(sleepTime);
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          throw new RuntimeException("재시도 대기 중 인터럽트 발생", ie);
        }

        // Exponential backoff: 2배씩 증가
        backoffMs *= 2;
      }
    }

    throw lastError;
  }
}
