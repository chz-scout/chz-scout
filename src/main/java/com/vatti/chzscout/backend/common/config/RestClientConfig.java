package com.vatti.chzscout.backend.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/** RestClient 빈 설정. */
@Configuration
public class RestClientConfig {

  @Bean
  public RestClient restClient(RestClient.Builder builder) {
    return builder.build();
  }
}
