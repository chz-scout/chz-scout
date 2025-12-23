package com.vatti.chzscout.backend.discord.presentation.listener;

import com.vatti.chzscout.backend.ai.domain.event.AiMessageResponseReceivedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** AI 응답 이벤트를 수신하여 Discord로 전송하는 리스너. */
@Slf4j
@Component
@ConditionalOnBean(JDA.class)
@RequiredArgsConstructor
public class AiResponseListener {

  private final JDA jda;

  @EventListener
  public void handleAiResponse(AiMessageResponseReceivedEvent event) {
    TextChannel channel = jda.getTextChannelById(event.channelId());

    if (channel == null) {
      log.warn("채널을 찾을 수 없음: {}", event.channelId());
      return;
    }

    channel
        .sendMessage(event.response())
        .queue(
            success -> log.info("메시지 전송 성공: channelId={}", event.channelId()),
            failure -> log.error("메시지 전송 실패: channelId={}", event.channelId(), failure));
  }
}
