package com.vatti.chzscout.backend.ai.config;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAI 클라이언트 Bean 설정.
 *
 * <p>OpenAI Java SDK의 클라이언트를 Spring Bean으로 등록합니다.
 */
@Configuration
@RequiredArgsConstructor
public class OpenAiConfig {

  private final OpenAiProperties properties;

  /**
   * OpenAI API 클라이언트 Bean 생성.
   *
   * @return OpenAIClient 인스턴스
   */
  @Bean
  public OpenAIClient openAIClient() {
    return OpenAIOkHttpClient.builder().apiKey(properties.getKey()).build();
  }
}
