package com.vatti.chzscout.backend.ai.config;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
    return OpenAIOkHttpClient.builder()
        .apiKey(properties.getKey())
        .baseUrl(properties.getBaseUrl())
        .build();
  }

  /**
   * AI 작업 전용 Virtual Thread Executor.
   *
   * <p>OpenAI API 호출은 I/O 대기 시간이 길어 Virtual Thread 사용이 효과적입니다. destroyMethod로 애플리케이션 종료 시
   * ExecutorService를 정리합니다.
   *
   * @return Virtual Thread 기반 ExecutorService
   */
  @Bean(name = "aiExecutor", destroyMethod = "shutdown")
  public ExecutorService aiExecutor() {
    return Executors.newVirtualThreadPerTaskExecutor();
  }
}
