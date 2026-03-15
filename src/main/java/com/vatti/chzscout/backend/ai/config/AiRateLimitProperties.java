package com.vatti.chzscout.backend.ai.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI API Rate Limit 설정 프로퍼티.
 *
 * <p>application.yml의 ai.rate-limit 설정을 바인딩합니다.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "ai.rate-limit")
public class AiRateLimitProperties {

  /** 동시 요청 수 제한 (Semaphore permits). */
  private int permits = 200;

  /** 최대 재시도 횟수. */
  private int maxRetries = 3;

  /** 초기 백오프 시간 (밀리초). */
  private long initialBackoffMs = 1000;
}
