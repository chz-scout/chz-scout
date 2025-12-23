package com.vatti.chzscout.backend.discord.presentation.listener;

import com.vatti.chzscout.backend.ai.domain.event.AiMessageResponseReceivedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/** Discord 메시지 수신 리스너. */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageListener extends ListenerAdapter {

  private static final int MIN_LENGTH = 2;
  private static final int MAX_LENGTH = 500;

  private final ApplicationEventPublisher eventPublisher;

  @Override
  public void onMessageReceived(MessageReceivedEvent event) {
    // 봇이 보낸 메시지는 무시 (무한 루프 방지)
    if (event.getAuthor().isBot()) {
      return;
    }

    String content = event.getMessage().getContentRaw().trim();
    String authorName = event.getAuthor().getName();
    MessageChannelUnion channel = event.getChannel();

    log.info("메시지 수신: {} - {}", authorName, content);

    // TODO(human): 메시지 검증 로직 구현
    // 검증 실패 시 channel.sendMessage("안내 메시지").queue() 호출

    AiMessageResponseReceivedEvent responseEvent =
        new AiMessageResponseReceivedEvent(channel.getIdLong(), "테스트 임시 응답입니다.");
    eventPublisher.publishEvent(responseEvent);
  }
}
