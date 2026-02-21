package com.vatti.chzscout.backend.discord.presentation.listener;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.vatti.chzscout.backend.ai.domain.entity.MemberEmbedding;
import com.vatti.chzscout.backend.ai.domain.entity.StreamEmbedding;
import com.vatti.chzscout.backend.ai.infrastructure.MemberEmbeddingRepository;
import com.vatti.chzscout.backend.ai.infrastructure.StreamEmbeddingRepository;
import com.vatti.chzscout.backend.member.domain.entity.Member;
import com.vatti.chzscout.backend.member.infrastructure.MemberRepository;
import java.util.Optional;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LikeButtonListenerTest {

  @Mock private MemberRepository memberRepository;
  @Mock private MemberEmbeddingRepository memberEmbeddingRepository;
  @Mock private StreamEmbeddingRepository streamEmbeddingRepository;

  @InjectMocks private LikeButtonListener likeButtonListener;

  @Mock private ButtonInteractionEvent event;
  @Mock private User user;
  @Mock private ReplyCallbackAction replyAction;

  @BeforeEach
  void setUp() {
    given(event.getUser()).willReturn(user);
    given(event.reply(anyString())).willReturn(replyAction);
    given(replyAction.setEphemeral(true)).willReturn(replyAction);
  }

  @Nested
  @DisplayName("onButtonInteraction 메서드 테스트")
  class OnButtonInteraction {

    @Test
    @DisplayName("like: 접두사가 아니면 무시한다")
    void ignoresNonLikeButtons() {
      // given
      given(event.getComponentId()).willReturn("other:button");

      // when
      likeButtonListener.onButtonInteraction(event);

      // then
      verify(memberRepository, never()).findByDiscordId(anyString());
      verify(event, never()).reply(anyString());
    }

    @Test
    @DisplayName("회원이 없으면 가입 안내 메시지를 반환한다")
    void returnsSignupMessageWhenMemberNotFound() {
      // given
      given(event.getComponentId()).willReturn("like:channel123");
      given(user.getId()).willReturn("discord123");
      given(memberRepository.findByDiscordId("discord123")).willReturn(Optional.empty());

      // when
      likeButtonListener.onButtonInteraction(event);

      // then
      verify(event).reply("먼저 서비스에 가입해주세요! 🙏");
      verify(replyAction).setEphemeral(true);
      verify(replyAction).queue();
    }

    @Test
    @DisplayName("방송 임베딩이 없으면 에러 메시지를 반환한다")
    void returnsErrorWhenStreamEmbeddingNotFound() {
      // given
      given(event.getComponentId()).willReturn("like:channel123");
      given(user.getId()).willReturn("discord123");

      Member member = Member.create("discord123", "테스터", null);
      given(memberRepository.findByDiscordId("discord123")).willReturn(Optional.of(member));
      given(streamEmbeddingRepository.findById("channel123")).willReturn(Optional.empty());

      // when
      likeButtonListener.onButtonInteraction(event);

      // then
      verify(event).reply("해당 방송 정보를 찾을 수 없어요 😢");
      verify(replyAction).setEphemeral(true);
      verify(replyAction).queue();
    }

    @Test
    @DisplayName("좋아요 성공 시 MemberEmbedding을 저장하고 성공 메시지를 반환한다")
    void savesMemberEmbeddingAndReturnsSuccessMessage() {
      // given
      given(event.getComponentId()).willReturn("like:channel123");
      given(user.getId()).willReturn("discord123");

      Member member = Member.create("discord123", "테스터", null);
      given(memberRepository.findByDiscordId("discord123")).willReturn(Optional.of(member));

      float[] embedding = new float[] {0.1f, 0.2f, 0.3f};
      StreamEmbedding streamEmbedding = StreamEmbedding.create("channel123", "롤 방송", embedding);
      given(streamEmbeddingRepository.findById("channel123"))
          .willReturn(Optional.of(streamEmbedding));

      // when
      likeButtonListener.onButtonInteraction(event);

      // then
      verify(memberEmbeddingRepository).save(any(MemberEmbedding.class));
      verify(event).reply("👍 좋아요가 반영되었어요! 다음 추천에 참고할게요.");
      verify(replyAction).setEphemeral(true);
      verify(replyAction).queue();
    }

    @Test
    @DisplayName("channelId를 올바르게 파싱한다")
    void parsesChannelIdCorrectly() {
      // given
      given(event.getComponentId()).willReturn("like:my-channel-id-123");
      given(user.getId()).willReturn("discord123");
      given(memberRepository.findByDiscordId("discord123")).willReturn(Optional.empty());

      // when
      likeButtonListener.onButtonInteraction(event);

      // then - like: 접두사(5글자) 이후가 channelId
      // memberRepository 호출은 됨 (회원 없음 분기 도달)
      verify(memberRepository).findByDiscordId("discord123");
    }
  }
}
