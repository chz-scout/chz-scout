package com.vatti.chzscout.backend.discord.presentation.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.vatti.chzscout.backend.ai.application.AiChatService;
import com.vatti.chzscout.backend.ai.application.VectorRecommendService;
import com.vatti.chzscout.backend.ai.domain.dto.UserMessageAnalysisResult;
import com.vatti.chzscout.backend.ai.domain.event.AiMessageResponseReceivedEvent;
import com.vatti.chzscout.backend.member.domain.entity.Member;
import com.vatti.chzscout.backend.member.infrastructure.MemberRepository;
import com.vatti.chzscout.backend.stream.domain.Stream;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
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
  @Mock VectorRecommendService vectorRecommendService;
  @Mock MemberRepository memberRepository;

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
    given(author.getId()).willReturn("123456789");
    given(channel.getIdLong()).willReturn(123456789L);
    given(channel.sendMessage(anyString())).willReturn(messageCreateAction);

    // 비회원 기본 설정 (개인화 추천 없이 일반 추천 사용)
    given(memberRepository.findByDiscordId(anyString())).willReturn(Optional.empty());
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
      verify(aiChatService, never()).analyzeUserMessageAsync(anyString());
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
      verify(aiChatService, never()).analyzeUserMessageAsync(anyString());
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
      verify(aiChatService, never()).analyzeUserMessageAsync(anyString());
    }

    @Test
    @DisplayName("추천 요청 시 벡터 기반 방송 추천 결과를 개별 메시지로 전송한다")
    void sendsIndividualMessagesForRecommendationResults() throws InterruptedException {
      // given
      given(author.isBot()).willReturn(false);
      given(message.getContentRaw()).willReturn("롤 방송 추천해줘");

      UserMessageAnalysisResult analysisResult =
          new UserMessageAnalysisResult("recommendation", List.of(), List.of("롤"), null);
      given(aiChatService.analyzeUserMessageAsync("롤 방송 추천해줘"))
          .willReturn(CompletableFuture.completedFuture(analysisResult));

      List<Stream> streams =
          List.of(
              new Stream(1, "롤 방송1", "thumb1.jpg", 1000, "ch1", "스트리머1", "리그 오브 레전드", List.of("롤")),
              new Stream(2, "롤 방송2", "thumb2.jpg", 500, "ch2", "스트리머2", "리그 오브 레전드", List.of("롤")));
      // 검색 쿼리: "롤 롤 롤 롤 롤" (5번 반복)
      given(vectorRecommendService.recommend(anyString(), eq(5))).willReturn(streams);

      // sendMessage().addActionRow().queue() 체인 설정
      given(
              messageCreateAction.addActionRow(
                  any(net.dv8tion.jda.api.interactions.components.buttons.Button.class)))
          .willReturn(messageCreateAction);

      // when
      messageListener.onMessageReceived(event);
      Thread.sleep(100); // 비동기 처리 대기

      // then - 각 방송에 대해 개별 메시지가 전송되어야 함
      verify(channel).sendMessage(contains("https://chzzk.naver.com/live/ch1"));
      verify(channel).sendMessage(contains("https://chzzk.naver.com/live/ch2"));
    }

    @Test
    @DisplayName("추천 결과가 없으면 안내 메시지를 이벤트로 발행한다")
    void publishesNoResultsMessage() throws InterruptedException {
      // given
      given(author.isBot()).willReturn(false);
      given(message.getContentRaw()).willReturn("특이한게임 방송 추천해줘");

      UserMessageAnalysisResult analysisResult =
          new UserMessageAnalysisResult("recommendation", List.of(), List.of("특이한게임"), null);
      given(aiChatService.analyzeUserMessageAsync("특이한게임 방송 추천해줘"))
          .willReturn(CompletableFuture.completedFuture(analysisResult));
      given(vectorRecommendService.recommend(anyString(), eq(5))).willReturn(List.of());

      // when
      messageListener.onMessageReceived(event);
      Thread.sleep(100); // 비동기 처리 대기

      // then
      ArgumentCaptor<AiMessageResponseReceivedEvent> captor =
          ArgumentCaptor.forClass(AiMessageResponseReceivedEvent.class);
      verify(eventPublisher).publishEvent(captor.capture());

      AiMessageResponseReceivedEvent capturedEvent = captor.getValue();
      assertThat(capturedEvent.channelId()).isEqualTo(123456789L);
      assertThat(capturedEvent.response()).contains("조건에 맞는 방송이 없어요");
    }

    @Test
    @DisplayName("greeting 의도면 GPT 응답을 이벤트로 발행한다")
    void publishesDirectReplyForGreeting() throws InterruptedException {
      // given
      given(author.isBot()).willReturn(false);
      given(message.getContentRaw()).willReturn("안녕하세요");

      UserMessageAnalysisResult analysisResult =
          new UserMessageAnalysisResult("greeting", List.of(), List.of(), "안녕하세요! 무엇을 도와드릴까요?");
      given(aiChatService.analyzeUserMessageAsync("안녕하세요"))
          .willReturn(CompletableFuture.completedFuture(analysisResult));

      // when
      messageListener.onMessageReceived(event);
      Thread.sleep(100); // 비동기 처리 대기

      // then
      ArgumentCaptor<AiMessageResponseReceivedEvent> captor =
          ArgumentCaptor.forClass(AiMessageResponseReceivedEvent.class);
      verify(eventPublisher).publishEvent(captor.capture());

      AiMessageResponseReceivedEvent capturedEvent = captor.getValue();
      assertThat(capturedEvent.channelId()).isEqualTo(123456789L);
      assertThat(capturedEvent.response()).isEqualTo("안녕하세요! 무엇을 도와드릴까요?");
      verify(vectorRecommendService, never()).recommend(anyString(), anyInt());
    }

    @Test
    @DisplayName("other 의도면 fallback 메시지를 이벤트로 발행한다")
    void publishesFallbackForOther() throws InterruptedException {
      // given
      given(author.isBot()).willReturn(false);
      given(message.getContentRaw()).willReturn("뭐해?");

      UserMessageAnalysisResult analysisResult =
          new UserMessageAnalysisResult("other", List.of(), List.of(), null);
      given(aiChatService.analyzeUserMessageAsync("뭐해?"))
          .willReturn(CompletableFuture.completedFuture(analysisResult));

      // when
      messageListener.onMessageReceived(event);
      Thread.sleep(100); // 비동기 처리 대기

      // then
      ArgumentCaptor<AiMessageResponseReceivedEvent> captor =
          ArgumentCaptor.forClass(AiMessageResponseReceivedEvent.class);
      verify(eventPublisher).publishEvent(captor.capture());

      AiMessageResponseReceivedEvent capturedEvent = captor.getValue();
      assertThat(capturedEvent.response()).contains("요청을 이해하지 못했어요");
      verify(vectorRecommendService, never()).recommend(anyString(), anyInt());
    }

    @Test
    @DisplayName("AI 서비스 예외 발생 시 에러 메시지를 이벤트로 발행한다")
    void publishesErrorWhenAiServiceFails() throws InterruptedException {
      // given
      given(author.isBot()).willReturn(false);
      given(message.getContentRaw()).willReturn("롤 방송 추천해줘");
      given(aiChatService.analyzeUserMessageAsync(anyString()))
          .willReturn(CompletableFuture.failedFuture(new RuntimeException("AI 서비스 오류")));

      // when
      messageListener.onMessageReceived(event);
      Thread.sleep(100); // 비동기 처리 대기

      // then
      ArgumentCaptor<AiMessageResponseReceivedEvent> captor =
          ArgumentCaptor.forClass(AiMessageResponseReceivedEvent.class);
      verify(eventPublisher).publishEvent(captor.capture());

      AiMessageResponseReceivedEvent capturedEvent = captor.getValue();
      assertThat(capturedEvent.response()).contains("죄송해요");
    }

    @Test
    @DisplayName("semantic_tags와 keywords를 조합하여 검색 쿼리를 생성한다")
    void buildsSearchQueryWithTagsAndKeywords() throws InterruptedException {
      // given
      given(author.isBot()).willReturn(false);
      given(message.getContentRaw()).willReturn("빡센 롤 방송 추천해줘");

      UserMessageAnalysisResult analysisResult =
          new UserMessageAnalysisResult("recommendation", List.of("빡겜"), List.of("롤"), null);
      given(aiChatService.analyzeUserMessageAsync("빡센 롤 방송 추천해줘"))
          .willReturn(CompletableFuture.completedFuture(analysisResult));
      given(vectorRecommendService.recommend(anyString(), eq(5))).willReturn(List.of());

      // when
      messageListener.onMessageReceived(event);
      Thread.sleep(100); // 비동기 처리 대기

      // then - 검색 쿼리: "롤 롤 롤 롤 롤 빡겜" (primary keyword 5번 + semantic tags)
      ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
      verify(vectorRecommendService).recommend(queryCaptor.capture(), eq(5));

      String capturedQuery = queryCaptor.getValue();
      assertThat(capturedQuery).contains("롤 롤 롤 롤 롤"); // 5번 반복
      assertThat(capturedQuery).contains("빡겜"); // semantic tag 포함
    }

    @Test
    @DisplayName("여러 키워드가 있으면 첫 번째만 5번 반복하고 나머지는 1번씩 포함한다")
    void buildsSearchQueryWithMultipleKeywords() throws InterruptedException {
      // given
      given(author.isBot()).willReturn(false);
      given(message.getContentRaw()).willReturn("여자 롤 방송 추천해줘");

      UserMessageAnalysisResult analysisResult =
          new UserMessageAnalysisResult(
              "recommendation", List.of("실력방송"), List.of("롤", "여자"), null);
      given(aiChatService.analyzeUserMessageAsync("여자 롤 방송 추천해줘"))
          .willReturn(CompletableFuture.completedFuture(analysisResult));
      given(vectorRecommendService.recommend(anyString(), eq(5))).willReturn(List.of());

      // when
      messageListener.onMessageReceived(event);
      Thread.sleep(100); // 비동기 처리 대기

      // then - 검색 쿼리: "롤 롤 롤 롤 롤 여자 실력방송"
      ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
      verify(vectorRecommendService).recommend(queryCaptor.capture(), eq(5));

      String capturedQuery = queryCaptor.getValue();
      // 첫 번째 키워드(롤) 5번 반복 확인
      long rolCount =
          capturedQuery.split(" ").length
              - capturedQuery.replace("롤", "").split(" ").length
              + (capturedQuery.contains("롤") ? 1 : 0);
      assertThat(capturedQuery).startsWith("롤 롤 롤 롤 롤");
      // 두 번째 키워드(여자)와 semantic tag(실력방송) 포함 확인
      assertThat(capturedQuery).contains("여자");
      assertThat(capturedQuery).contains("실력방송");
    }

    @Test
    @DisplayName("search 의도이고 reply가 있으면 해당 응답을 이벤트로 발행한다")
    void publishesReplyForSearch() throws InterruptedException {
      // given
      given(author.isBot()).willReturn(false);
      given(message.getContentRaw()).willReturn("우왁굳 방송해?");

      UserMessageAnalysisResult analysisResult =
          new UserMessageAnalysisResult(
              "search", List.of(), List.of("우왁굳"), "현재 우왁굳님은 방송 중이 아닙니다.");
      given(aiChatService.analyzeUserMessageAsync("우왁굳 방송해?"))
          .willReturn(CompletableFuture.completedFuture(analysisResult));

      // when
      messageListener.onMessageReceived(event);
      Thread.sleep(100); // 비동기 처리 대기

      // then
      ArgumentCaptor<AiMessageResponseReceivedEvent> captor =
          ArgumentCaptor.forClass(AiMessageResponseReceivedEvent.class);
      verify(eventPublisher).publishEvent(captor.capture());

      AiMessageResponseReceivedEvent capturedEvent = captor.getValue();
      assertThat(capturedEvent.response()).contains("우왁굳님은 방송 중이 아닙니다");
      verify(vectorRecommendService, never()).recommend(anyString(), anyInt());
    }
  }

  @Nested
  @DisplayName("개인화 추천 테스트")
  class PersonalizedRecommendation {

    @Mock Member member;

    @Test
    @DisplayName("등록된 회원이면 개인화 추천을 사용한다")
    void usesPersonalizedRecommendationForRegisteredMember() throws InterruptedException {
      // given
      given(author.isBot()).willReturn(false);
      given(message.getContentRaw()).willReturn("롤 방송 추천해줘");
      given(memberRepository.findByDiscordId("123456789")).willReturn(Optional.of(member));

      UserMessageAnalysisResult analysisResult =
          new UserMessageAnalysisResult("recommendation", List.of(), List.of("롤"), null);
      given(aiChatService.analyzeUserMessageAsync("롤 방송 추천해줘"))
          .willReturn(CompletableFuture.completedFuture(analysisResult));

      List<Stream> streams =
          List.of(
              new Stream(1, "롤 방송", "thumb.jpg", 1000, "ch1", "스트리머", "리그 오브 레전드", List.of("롤")));
      // 개인화 추천 메서드 (3개 파라미터) 호출
      given(vectorRecommendService.recommend(anyString(), eq(member), eq(5))).willReturn(streams);

      given(
              messageCreateAction.addActionRow(
                  any(net.dv8tion.jda.api.interactions.components.buttons.Button.class)))
          .willReturn(messageCreateAction);

      // when
      messageListener.onMessageReceived(event);
      Thread.sleep(100);

      // then - 개인화 추천 메서드가 호출되어야 함
      verify(vectorRecommendService).recommend(anyString(), eq(member), eq(5));
      verify(vectorRecommendService, never()).recommend(anyString(), eq(5));
    }

    @Test
    @DisplayName("비회원이면 일반 추천을 사용한다")
    void usesBasicRecommendationForNonMember() throws InterruptedException {
      // given
      given(author.isBot()).willReturn(false);
      given(message.getContentRaw()).willReturn("롤 방송 추천해줘");
      given(memberRepository.findByDiscordId("123456789")).willReturn(Optional.empty());

      UserMessageAnalysisResult analysisResult =
          new UserMessageAnalysisResult("recommendation", List.of(), List.of("롤"), null);
      given(aiChatService.analyzeUserMessageAsync("롤 방송 추천해줘"))
          .willReturn(CompletableFuture.completedFuture(analysisResult));

      List<Stream> streams =
          List.of(
              new Stream(1, "롤 방송", "thumb.jpg", 1000, "ch1", "스트리머", "리그 오브 레전드", List.of("롤")));
      // 일반 추천 메서드 (2개 파라미터) 호출
      given(vectorRecommendService.recommend(anyString(), eq(5))).willReturn(streams);

      given(
              messageCreateAction.addActionRow(
                  any(net.dv8tion.jda.api.interactions.components.buttons.Button.class)))
          .willReturn(messageCreateAction);

      // when
      messageListener.onMessageReceived(event);
      Thread.sleep(100);

      // then - 일반 추천 메서드가 호출되어야 함
      verify(vectorRecommendService).recommend(anyString(), eq(5));
      verify(vectorRecommendService, never()).recommend(anyString(), any(Member.class), anyInt());
    }

    @Test
    @DisplayName("회원의 Discord ID로 조회한다")
    void queriesMemberByDiscordId() throws InterruptedException {
      // given
      given(author.isBot()).willReturn(false);
      given(author.getId()).willReturn("987654321");
      given(message.getContentRaw()).willReturn("롤 방송 추천해줘");

      UserMessageAnalysisResult analysisResult =
          new UserMessageAnalysisResult("recommendation", List.of(), List.of("롤"), null);
      given(aiChatService.analyzeUserMessageAsync("롤 방송 추천해줘"))
          .willReturn(CompletableFuture.completedFuture(analysisResult));
      given(vectorRecommendService.recommend(anyString(), eq(5))).willReturn(List.of());

      // when
      messageListener.onMessageReceived(event);
      Thread.sleep(100);

      // then - Discord ID로 회원 조회
      verify(memberRepository).findByDiscordId("987654321");
    }
  }
}
