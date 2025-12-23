package com.vatti.chzscout.backend.discord.presentation.listener;

import com.vatti.chzscout.backend.ai.domain.event.AiMessageResponseReceivedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/** Discord 메시지 수신 리스너. */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageListener extends ListenerAdapter {

  private final ApplicationEventPublisher eventPublisher;

  @Override
  public void onMessageReceived(MessageReceivedEvent event) {
    // 봇이 보낸 메시지는 무시 (무한 루프 방지)
    if (event.getAuthor().isBot()) {
      return;
    }

    String content = event.getMessage().getContentRaw();
    String authorName = event.getAuthor().getName();

    log.info("메시지 수신: {} - {}", authorName, content);

    AiMessageResponseReceivedEvent responseEvent =
        new AiMessageResponseReceivedEvent(event.getChannel().getIdLong(), "테스트 임시 응답입니다.");
    eventPublisher.publishEvent(responseEvent);
  }
}
