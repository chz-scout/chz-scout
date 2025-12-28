package com.vatti.chzscout.backend.discord.presentation.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.vatti.chzscout.backend.ai.application.AiChatService;
import com.vatti.chzscout.backend.ai.domain.dto.UserMessageAnalysisResult;
import com.vatti.chzscout.backend.ai.domain.event.AiMessageResponseReceivedEvent;
import com.vatti.chzscout.backend.stream.application.service.StreamRecommendationService;
import com.vatti.chzscout.backend.stream.domain.Stream;
import java.util.List;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MessageListenerTest {

  @Mock ApplicationEventPublisher eventPublisher;
  @Mock AiChatService aiChatService;
  @Mock StreamRecommendationService streamRecommendationService;

  @InjectMocks MessageListener messageListener;

  @Mock MessageReceivedEvent event;
  @Mock Message message;
  @Mock User author;
  @Mock MessageChannelUnion channel;
  @Mock MessageCreateAction messageCreateAction;

  @BeforeEach
  void setUp() {
    given(event.getAuthor()).willReturn(author);
    given(event.getMessage()).willReturn(message);
    given(event.getChannel()).willReturn(channel);
    given(author.getName()).willReturn("테스트유저");
    given(channel.getIdLong()).willReturn(123456789L);
    given(channel.sendMessage(anyString())).willReturn(messageCreateAction);
  }

  @Nested
  @DisplayName("onMessageReceived 메서드 테스트")
  class OnMessageReceived {

    @Test
    @DisplayName("봇이 보낸 메시지는 무시한다")
    void ignoresBotMessage() {
      // given
      given(author.isBot()).willReturn(true);

      // when
      messageListener.onMessageReceived(event);

      // then
      verify(channel, never()).sendMessage(anyString());
      verify(aiChatService, never()).analyzeUserMessage(anyString());
      verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("메시지가 2자 미만이면 에러 메시지를 전송한다")
    void sendsErrorWhenMessageTooShort() {
      // given
      given(author.isBot()).willReturn(false);
      given(message.getContentRaw()).willReturn("a");

      // when
      messageListener.onMessageReceived(event);

      // then
      verify(channel).sendMessage(contains("너무 짧아요"));
      verify(messageCreateAction).queue();
      verify(aiChatService, never()).analyzeUserMessage(anyString());
    }

    @Test
    @DisplayName("메시지가 500자 초과면 에러 메시지를 전송한다")
    void sendsErrorWhenMessageTooLong() {
      // given
      given(author.isBot()).willReturn(false);
      String longMessage = "a".repeat(501);
      given(message.getContentRaw()).willReturn(longMessage);

      // when
      messageListener.onMessageReceived(event);

      // then
      verify(channel).sendMessage(contains("너무 길어요"));
      verify(messageCreateAction).queue();
      verify(aiChatService, never()).analyzeUserMessage(anyString());
    }

    @Test
    @DisplayName("추천 요청 시 방송 추천 결과를 이벤트로 발행한다")
    void publishesRecommendationResults() {
      // given
      given(author.isBot()).willReturn(false);
      given(message.getContentRaw()).willReturn("롤 방송 추천해줘");

      UserMessageAnalysisResult analysisResult =
          new UserMessageAnalysisResult("recommendation", List.of(), List.of("롤"), null);
      given(aiChatService.analyzeUserMessage("롤 방송 추천해줘")).willReturn(analysisResult);

      List<Stream> streams =
          List.of(
              new Stream(1, "롤 방송1", "thumb1.jpg", 1000, "ch1", "스트리머1", "리그 오브 레전드", List.of("롤")),
              new Stream(2, "롤 방송2", "thumb2.jpg", 500, "ch2", "스트리머2", "리그 오브 레전드", List.of("롤")));
      given(streamRecommendationService.recommend(List.of("롤"))).willReturn(streams);

      // when
      messageListener.onMessageReceived(event);

      // then
      ArgumentCaptor<AiMessageResponseReceivedEvent> captor =
          ArgumentCaptor.forClass(AiMessageResponseReceivedEvent.class);
      verify(eventPublisher).publishEvent(captor.capture());

      AiMessageResponseReceivedEvent capturedEvent = captor.getValue();
      assertThat(capturedEvent.channelId()).isEqualTo(123456789L);
      assertThat(capturedEvent.response())
          .contains("https://chzzk.naver.com/live/ch1")
          .contains("https://chzzk.naver.com/live/ch2");
    }

    @Test
    @DisplayName("추천 결과가 없으면 안내 메시지를 이벤트로 발행한다")
    void publishesNoResultsMessage() {
      // given
      given(author.isBot()).willReturn(false);
      given(message.getContentRaw()).willReturn("특이한게임 방송 추천해줘");

      UserMessageAnalysisResult analysisResult =
          new UserMessageAnalysisResult("recommendation", List.of(), List.of("특이한게임"), null);
      given(aiChatService.analyzeUserMessage("특이한게임 방송 추천해줘")).willReturn(analysisResult);
      given(streamRecommendationService.recommend(List.of("특이한게임"))).willReturn(List.of());

      // when
      messageListener.onMessageReceived(event);

      // then
      ArgumentCaptor<AiMessageResponseReceivedEvent> captor =
          ArgumentCaptor.forClass(AiMessageResponseReceivedEvent.class);
      verify(eventPublisher).publishEvent(captor.capture());

      AiMessageResponseReceivedEvent capturedEvent = captor.getValue();
      assertThat(capturedEvent.channelId()).isEqualTo(123456789L);
      assertThat(capturedEvent.response()).contains("조건에 맞는 방송이 없어요");
    }

    @Test
    @DisplayName("other 의도면 GPT 응답을 이벤트로 발행한다")
    void publishesDirectReplyForOther() {
      // given
      given(author.isBot()).willReturn(false);
      given(message.getContentRaw()).willReturn("안녕하세요");

      UserMessageAnalysisResult analysisResult =
          new UserMessageAnalysisResult("other", List.of(), List.of(), "안녕하세요! 무엇을 도와드릴까요?");
      given(aiChatService.analyzeUserMessage("안녕하세요")).willReturn(analysisResult);

      // when
      messageListener.onMessageReceived(event);

      // then
      ArgumentCaptor<AiMessageResponseReceivedEvent> captor =
          ArgumentCaptor.forClass(AiMessageResponseReceivedEvent.class);
      verify(eventPublisher).publishEvent(captor.capture());

      AiMessageResponseReceivedEvent capturedEvent = captor.getValue();
      assertThat(capturedEvent.channelId()).isEqualTo(123456789L);
      assertThat(capturedEvent.response()).isEqualTo("안녕하세요! 무엇을 도와드릴까요?");
      verify(streamRecommendationService, never()).recommend(any());
    }

    @Test
    @DisplayName("AI 서비스 예외 발생 시 에러 메시지를 전송한다")
    void sendsErrorWhenAiServiceFails() {
      // given
      given(author.isBot()).willReturn(false);
      given(message.getContentRaw()).willReturn("롤 방송 추천해줘");
      given(aiChatService.analyzeUserMessage(anyString()))
          .willThrow(new RuntimeException("AI 서비스 오류"));

      // when
      messageListener.onMessageReceived(event);

      // then
      verify(channel).sendMessage(contains("죄송해요"));
      verify(messageCreateAction).queue();
      verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("의미 태그와 키워드를 합쳐서 추천 서비스에 전달한다")
    void combinesSemanticTagsAndKeywords() {
      // given
      given(author.isBot()).willReturn(false);
      given(message.getContentRaw()).willReturn("빡센 롤 방송 추천해줘");

      UserMessageAnalysisResult analysisResult =
          new UserMessageAnalysisResult("recommendation", List.of("빡겜"), List.of("롤"), null);
      given(aiChatService.analyzeUserMessage("빡센 롤 방송 추천해줘")).willReturn(analysisResult);
      given(streamRecommendationService.recommend(any())).willReturn(List.of());

      // when
      messageListener.onMessageReceived(event);

      // then
      verify(streamRecommendationService).recommend(List.of("빡겜", "롤"));
    }
  }
}
