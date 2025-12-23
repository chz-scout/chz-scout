package com.vatti.chzscout.backend.discord.config;

import static org.mockito.Mockito.mock;

import net.dv8tion.jda.api.JDA;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Discord 관련 테스트용 설정.
 *
 * <p>JDA Mock Bean을 제공합니다. 테스트에서 @Import(DiscordTestConfig.class)로 사용하세요.
 */
@TestConfiguration
public class DiscordTestConfig {

  @Bean
  public JDA jda() {
    return mock(JDA.class);
  }
}
