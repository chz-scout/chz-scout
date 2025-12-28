package com.vatti.chzscout.backend.ai.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * OpenAI API 설정 프로퍼티.
 *
 * <p>application.yml의 openai.api 설정을 바인딩합니다.
 */
@Component
@ConfigurationProperties(prefix = "openai.api")
@Validated
@Getter
@Setter
public class OpenAiProperties {

  /** OpenAI API 키. */
  @NotBlank(message = "OpenAI API 키는 필수입니다")
  private String key;

  /** 사용할 모델명 (예: gpt-5-nano). */
  @NotBlank(message = "OpenAI 모델명은 필수입니다")
  private String model;

  /** API 기본 URL. */
  @NotBlank(message = "OpenAI API URL은 필수입니다")
  private String baseUrl;
}
