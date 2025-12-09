package com.vatti.chzscout.backend.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

  @Bean
  public OpenAPI openAPI() {
    return new OpenAPI()
        .info(
            new Info()
                .title("CHZ-Scout API")
                .description("치지직(Chzzk) 실시간 스트리밍 추천 Discord 챗봇 API")
                .version("v1.0.0")
                .contact(new Contact().name("Vatti").email("contact@vatti.com")))
        .servers(
            List.of(
                new Server().url("http://localhost:8080/api").description("Local Server"),
                new Server().url("https://dev.chz-scout.com/api").description("Dev Server"),
                new Server().url("https://chz-scout.com/api").description("Production Server")));
  }
}