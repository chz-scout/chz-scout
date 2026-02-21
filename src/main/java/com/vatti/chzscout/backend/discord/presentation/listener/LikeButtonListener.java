package com.vatti.chzscout.backend.discord.presentation.listener;

import com.vatti.chzscout.backend.ai.domain.entity.MemberEmbedding;
import com.vatti.chzscout.backend.ai.domain.entity.StreamEmbedding;
import com.vatti.chzscout.backend.ai.infrastructure.MemberEmbeddingRepository;
import com.vatti.chzscout.backend.ai.infrastructure.StreamEmbeddingRepository;
import com.vatti.chzscout.backend.member.domain.entity.Member;
import com.vatti.chzscout.backend.member.infrastructure.MemberRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 좋아요 버튼 클릭 이벤트 리스너.
 *
 * <p>사용자가 추천 방송의 좋아요 버튼을 클릭하면 해당 방송의 임베딩을 MemberEmbedding에 저장합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LikeButtonListener extends ListenerAdapter {

  private final MemberRepository memberRepository;
  private final MemberEmbeddingRepository memberEmbeddingRepository;
  private final StreamEmbeddingRepository streamEmbeddingRepository;

  @Override
  @Transactional
  public void onButtonInteraction(ButtonInteractionEvent event) {
    String componentId = event.getComponentId();

    // like:{channelId} 형식인지 확인
    if (!componentId.startsWith("like:")) {
      return;
    }

    String channelId = componentId.substring(5);
    String discordId = event.getUser().getId();

    log.info("좋아요 버튼 클릭 - discordId: {}, channelId: {}", discordId, channelId);

    // 1. Member 조회
    Optional<Member> memberOpt = memberRepository.findByDiscordId(discordId);
    if (memberOpt.isEmpty()) {
      event.reply("먼저 서비스에 가입해주세요! 🙏").setEphemeral(true).queue();
      return;
    }

    // 2. StreamEmbedding 조회
    Optional<StreamEmbedding> streamEmbeddingOpt = streamEmbeddingRepository.findById(channelId);
    if (streamEmbeddingOpt.isEmpty()) {
      event.reply("해당 방송 정보를 찾을 수 없어요 😢").setEphemeral(true).queue();
      return;
    }

    // 3. MemberEmbedding 저장
    Member member = memberOpt.get();
    StreamEmbedding streamEmbedding = streamEmbeddingOpt.get();

    MemberEmbedding memberEmbedding =
        MemberEmbedding.create(member, streamEmbedding.getEmbedding());
    memberEmbeddingRepository.save(memberEmbedding);

    log.info("MemberEmbedding 저장 완료 - memberId: {}, channelId: {}", member.getId(), channelId);

    // 4. 응답 (본인에게만 보이는 메시지)
    event.reply("👍 좋아요가 반영되었어요! 다음 추천에 참고할게요.").setEphemeral(true).queue();
  }
}
