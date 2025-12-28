package com.vatti.chzscout.backend.discord.presentation.listener;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.vatti.chzscout.backend.ai.domain.event.AiMessageResponseReceivedEvent;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AiResponseListenerTest {
  @Mock JDA jda;
  @Mock TextChannel channel;
  @Mock MessageCreateAction messageCreateAction;

  @InjectMocks AiResponseListener aiResponseListener;

  @Nested
  @DisplayName("handleAiResponse 메서드")
  class HandleAiResponse {

    @Test
    @DisplayName("채널이 존재하면 메시지를 전송한다")
    void sendsMessageWhenChannelExists() {
      // given
      long channelId = 123456789L;
      String response = "추천 방송 목록입니다!";
      AiMessageResponseReceivedEvent event =
          new AiMessageResponseReceivedEvent(channelId, response);

      given(jda.getTextChannelById(channelId)).willReturn(channel);
      given(channel.sendMessage(response)).willReturn(messageCreateAction);

      // when
      aiResponseListener.handleAiResponse(event);

      // then
      verify(channel).sendMessage(response);
      verify(messageCreateAction).queue(any(), any());
    }

    @Test
    @DisplayName("채널이 null이면 메시지를 전송하지 않는다")
    void doesNotSendMessageWhenChannelIsNull() {
      // given
      long channelId = 999999999L;
      AiMessageResponseReceivedEvent event =
          new AiMessageResponseReceivedEvent(channelId, "응답 메시지");

      given(jda.getTextChannelById(channelId)).willReturn(null);

      // when
      aiResponseListener.handleAiResponse(event);

      // then
      verify(channel, never()).sendMessage(any(String.class));
    }
  }
}
