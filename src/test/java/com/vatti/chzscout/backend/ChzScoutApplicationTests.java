package com.vatti.chzscout.backend;

import com.vatti.chzscout.backend.discord.config.DiscordTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(DiscordTestConfig.class)
class ChzScoutApplicationTests {

  @Test
  void contextLoads() {}
}
