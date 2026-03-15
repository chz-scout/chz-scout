package com.vatti.chzscout.backend.ai.infrastructure;

import com.vatti.chzscout.backend.ai.config.AiRateLimitProperties;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.concurrent.Semaphore;
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

  // TODO(human): 재시도 로직 구현
  private <T> T executeWithRetry(Supplier<T> action) {
    return action.get();
  }
}
