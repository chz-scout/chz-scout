package com.vatti.chzscout.backend.discord.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

import com.vatti.chzscout.backend.stream.domain.EnrichedStreamDto;
import com.vatti.chzscout.backend.stream.fixture.EnrichedStreamDtoFixture;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel;
import net.dv8tion.jda.api.requests.restaction.CacheRestAction;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DiscordDmServiceTest {

  @Mock private JDA jda;
  @Mock private CacheRestAction<User> userRestAction;
  @Mock private User user;
  @Mock private CacheRestAction<PrivateChannel> privateChannelRestAction;
  @Mock private PrivateChannel privateChannel;
  @Mock private MessageCreateAction messageCreateAction;

  @InjectMocks private DiscordDmService discordDmService;

  @Nested
  @DisplayName("sendNotification 메서드 테스트")
  class SendNotification {

    @Test
    @DisplayName("유저 조회 실패 시 false를 반환한다")
    void returnsFalseWhenUserRetrieveFails() {
      // given
      String discordId = "123456789";
      Set<EnrichedStreamDto> streams = Set.of(EnrichedStreamDtoFixture.create(1));

      given(jda.retrieveUserById(discordId)).willReturn(userRestAction);
      doAnswer(
              invocation -> {
                Consumer<Throwable> failureCallback = invocation.getArgument(1);
                failureCallback.accept(new RuntimeException("User not found"));
                return null;
              })
          .when(userRestAction)
          .queue(any(), any());

      // when
      CompletableFuture<Boolean> result = discordDmService.sendNotification(discordId, streams);

      // then
      assertThat(result.join()).isFalse();
    }

    @Test
    @DisplayName("Private Channel 열기 실패 시 false를 반환한다")
    void returnsFalseWhenPrivateChannelFails() {
      // given
      String discordId = "123456789";
      Set<EnrichedStreamDto> streams = Set.of(EnrichedStreamDtoFixture.create(1));

      given(jda.retrieveUserById(discordId)).willReturn(userRestAction);
      doAnswer(
              invocation -> {
                Consumer<User> successCallback = invocation.getArgument(0);
                successCallback.accept(user);
                return null;
              })
          .when(userRestAction)
          .queue(any(), any());

      given(user.openPrivateChannel()).willReturn(privateChannelRestAction);
      doAnswer(
              invocation -> {
                Consumer<Throwable> failureCallback = invocation.getArgument(1);
                failureCallback.accept(new RuntimeException("Cannot open DM"));
                return null;
              })
          .when(privateChannelRestAction)
          .queue(any(), any());

      // when
      CompletableFuture<Boolean> result = discordDmService.sendNotification(discordId, streams);

      // then
      assertThat(result.join()).isFalse();
    }

    @Test
    @DisplayName("메시지 발송 성공 시 true를 반환한다")
    void returnsTrueWhenMessageSentSuccessfully() {
      // given
      String discordId = "123456789";
      Set<EnrichedStreamDto> streams = Set.of(EnrichedStreamDtoFixture.create(1));

      given(jda.retrieveUserById(discordId)).willReturn(userRestAction);
      doAnswer(
              invocation -> {
                Consumer<User> successCallback = invocation.getArgument(0);
                successCallback.accept(user);
                return null;
              })
          .when(userRestAction)
          .queue(any(), any());

      given(user.openPrivateChannel()).willReturn(privateChannelRestAction);
      doAnswer(
              invocation -> {
                Consumer<PrivateChannel> successCallback = invocation.getArgument(0);
                successCallback.accept(privateChannel);
                return null;
              })
          .when(privateChannelRestAction)
          .queue(any(), any());

      given(privateChannel.sendMessage(anyString())).willReturn(messageCreateAction);
      doAnswer(
              invocation -> {
                Consumer<Object> successCallback = invocation.getArgument(0);
                successCallback.accept(null);
                return null;
              })
          .when(messageCreateAction)
          .queue(any(), any());

      given(user.getId()).willReturn(discordId);

      // when
      CompletableFuture<Boolean> result = discordDmService.sendNotification(discordId, streams);

      // then
      assertThat(result.join()).isTrue();
      verify(privateChannel).sendMessage(anyString());
    }

    @Test
    @DisplayName("메시지 발송 실패 시 false를 반환한다")
    void returnsFalseWhenMessageSendFails() {
      // given
      String discordId = "123456789";
      Set<EnrichedStreamDto> streams = Set.of(EnrichedStreamDtoFixture.create(1));

      given(jda.retrieveUserById(discordId)).willReturn(userRestAction);
      doAnswer(
              invocation -> {
                Consumer<User> successCallback = invocation.getArgument(0);
                successCallback.accept(user);
                return null;
              })
          .when(userRestAction)
          .queue(any(), any());

      given(user.openPrivateChannel()).willReturn(privateChannelRestAction);
      doAnswer(
              invocation -> {
                Consumer<PrivateChannel> successCallback = invocation.getArgument(0);
                successCallback.accept(privateChannel);
                return null;
              })
          .when(privateChannelRestAction)
          .queue(any(), any());

      given(privateChannel.sendMessage(anyString())).willReturn(messageCreateAction);
      doAnswer(
              invocation -> {
                Consumer<Throwable> failureCallback = invocation.getArgument(1);
                failureCallback.accept(new RuntimeException("Message send failed"));
                return null;
              })
          .when(messageCreateAction)
          .queue(any(), any());

      given(user.getId()).willReturn(discordId);

      // when
      CompletableFuture<Boolean> result = discordDmService.sendNotification(discordId, streams);

      // then
      assertThat(result.join()).isFalse();
    }
  }

  @Nested
  @DisplayName("buildNotificationMessage 메서드 테스트 (간접 검증)")
  class BuildNotificationMessage {

    @Test
    @DisplayName("메시지에 방송 링크가 포함된다")
    void messageContainsStreamLinks() {
      // given
      String discordId = "123456789";
      Set<EnrichedStreamDto> streams = new LinkedHashSet<>();
      streams.add(EnrichedStreamDtoFixture.create(1));
      streams.add(EnrichedStreamDtoFixture.create(2));

      given(jda.retrieveUserById(discordId)).willReturn(userRestAction);
      doAnswer(
              invocation -> {
                Consumer<User> successCallback = invocation.getArgument(0);
                successCallback.accept(user);
                return null;
              })
          .when(userRestAction)
          .queue(any(), any());

      given(user.openPrivateChannel()).willReturn(privateChannelRestAction);
      doAnswer(
              invocation -> {
                Consumer<PrivateChannel> successCallback = invocation.getArgument(0);
                successCallback.accept(privateChannel);
                return null;
              })
          .when(privateChannelRestAction)
          .queue(any(), any());

      given(privateChannel.sendMessage(anyString())).willReturn(messageCreateAction);
      doAnswer(
              invocation -> {
                Consumer<Object> successCallback = invocation.getArgument(0);
                successCallback.accept(null);
                return null;
              })
          .when(messageCreateAction)
          .queue(any(), any());

      given(user.getId()).willReturn(discordId);

      // when
      discordDmService.sendNotification(discordId, streams);

      // then - ArgumentCaptor로 메시지 내용 검증
      ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
      verify(privateChannel).sendMessage(messageCaptor.capture());

      String message = messageCaptor.getValue();
      assertThat(message).contains("다음 방송을 추천해드려요!");
      assertThat(message).contains("https://chzzk.naver.com/live/channel_1");
      assertThat(message).contains("https://chzzk.naver.com/live/channel_2");
    }
  }
}
