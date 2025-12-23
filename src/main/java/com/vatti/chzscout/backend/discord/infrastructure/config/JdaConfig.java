package com.vatti.chzscout.backend.discord.infrastructure.config;

import java.util.List;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Discord JDA 설정. 테스트 환경에서는 비활성화됨. */
@Configuration
@Profile("!test")
public class JdaConfig {

  @Value("${discord.bot.token}")
  private String botToken;

  @Bean
  public JDA jda(List<ListenerAdapter> listeners) {
    return JDABuilder.createDefault(botToken)
        .enableIntents(GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT)
        .addEventListeners(listeners.toArray())
        .build();
  }
}
