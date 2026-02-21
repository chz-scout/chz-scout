package com.vatti.chzscout.backend.common.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 커스텀 메트릭 설정.
 *
 * <p>추천 요청 수, AI API 호출 시간 등 비즈니스 메트릭을 정의합니다.
 */
@Configuration
public class MetricsConfig {

  /**
   * 추천 요청 카운터.
   *
   * <p>방송 추천 요청 횟수를 추적합니다.
   */
  @Bean
  public Counter recommendationRequestCounter(MeterRegistry registry) {
    return Counter.builder("chzscout.recommendation.requests")
        .description("Total number of stream recommendation requests")
        .tag("type", "stream")
        .register(registry);
  }

  /**
   * AI API 호출 타이머.
   *
   * <p>OpenAI API 호출 소요 시간을 측정합니다.
   */
  @Bean
  public Timer aiApiCallTimer(MeterRegistry registry) {
    return Timer.builder("chzscout.ai.api.duration")
        .description("Duration of OpenAI API calls")
        .publishPercentiles(0.5, 0.95, 0.99)
        .register(registry);
  }
}
