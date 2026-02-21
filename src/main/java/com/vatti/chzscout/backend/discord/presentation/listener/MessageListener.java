package com.vatti.chzscout.backend.discord.presentation.listener;

import com.vatti.chzscout.backend.ai.application.AiChatService;
import com.vatti.chzscout.backend.ai.application.VectorRecommendService;
import com.vatti.chzscout.backend.ai.domain.dto.UserMessageAnalysisResult;
import com.vatti.chzscout.backend.ai.domain.event.AiMessageResponseReceivedEvent;
import com.vatti.chzscout.backend.member.domain.entity.Member;
import com.vatti.chzscout.backend.member.infrastructure.MemberRepository;
import com.vatti.chzscout.backend.stream.domain.Stream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/** Discord 메시지 수신 리스너. */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageListener extends ListenerAdapter {

  private static final int MIN_LENGTH = 2;
  private static final int MAX_LENGTH = 500;
  private static final int RECOMMENDED_NUM = 5;

  private final ApplicationEventPublisher eventPublisher;
  private final AiChatService aiChatService;
  private final VectorRecommendService vectorRecommendService;
  private final MemberRepository memberRepository;

  @Override
  public void onMessageReceived(MessageReceivedEvent event) {
    // 봇이 보낸 메시지는 무시 (무한 루프 방지)
    if (event.getAuthor().isBot()) {
      return;
    }

    long startTime = System.nanoTime();
    String content = event.getMessage().getContentRaw().trim();
    String authorName = event.getAuthor().getName();
    MessageChannelUnion channel = event.getChannel();

    log.info("메시지 수신: {} - {}", authorName, content);

    // 길이 검증
    if (content.length() < MIN_LENGTH) {
      channel.sendMessage("메시지가 너무 짧아요! 2자 이상 입력해주세요 ✍️").queue();
      logElapsedTime(startTime, "유효성 검증 실패 (길이 부족)");
      return;
    }

    if (content.length() > MAX_LENGTH) {
      channel.sendMessage("메시지가 너무 길어요! 500자 이하로 입력해주세요 📝").queue();
      logElapsedTime(startTime, "유효성 검증 실패 (길이 초과)");
      return;
    }

    String discordId = event.getAuthor().getId();

    // 비동기로 GPT 분석 후 처리 (Virtual Thread Executor에서 실행)
    aiChatService
        .analyzeUserMessageAsync(content)
        .thenAccept(analysis -> handleAnalysisResult(channel, discordId, analysis, startTime))
        .exceptionally(
            e -> {
              log.error("메시지 처리 중 오류 발생: {}", e.getMessage(), e);
              publishResponse(channel, "죄송해요, 지금은 응답을 드리기 어려워요. 잠시 후 다시 시도해주세요! 🙏");
              logElapsedTime(startTime, "오류 발생");
              return null;
            });
  }

  /** 분석 결과를 처리합니다. */
  private void handleAnalysisResult(
      MessageChannelUnion channel,
      String discordId,
      UserMessageAnalysisResult analysis,
      long startTime) {
    log.info(
        "의도 분석 결과 - intent: {}, tags: {}, keywords: {}",
        analysis.getIntent(),
        analysis.getSemanticTags(),
        analysis.getKeywords());

    if (analysis.isRecommendationRequest()) {
      processMessageAsyncWithEmbedding(channel, discordId, analysis, startTime);
    } else if (analysis.hasDirectReply()) {
      publishResponse(channel, analysis.getReply());
      logElapsedTime(startTime, "직접 응답");
    } else {
      publishResponse(channel, "죄송해요, 요청을 이해하지 못했어요. '롤 방송 추천해줘'처럼 원하시는 방송 스타일을 말씀해주세요! 🎮");
      logElapsedTime(startTime, "의도 파악 실패");
    }
  }

  private void logElapsedTime(long startTimeNanos, String resultType) {
    long elapsedMs = (System.nanoTime() - startTimeNanos) / 1_000_000;
    log.info("응답 완료 - 결과: {}, 소요 시간: {}ms", resultType, elapsedMs);
  }

  /**
   * 벡터 임베딩 기반으로 방송을 추천합니다.
   *
   * <p>분석된 semantic_tags + keywords를 조합하여 검색 쿼리를 생성하고, 이를 임베딩하여 유사 방송을 검색합니다. 각 방송을 개별 메시지로 전송하며,
   * 좋아요 버튼을 첨부합니다.
   *
   * <p>등록된 회원인 경우 개인화된 추천을, 비회원인 경우 일반 추천을 제공합니다.
   */
  private void processMessageAsyncWithEmbedding(
      MessageChannelUnion channel,
      String discordId,
      UserMessageAnalysisResult analysis,
      long startTime) {
    try {
      // 1. semantic_tags + keywords를 조합하여 검색 쿼리 생성
      String searchQuery = buildSearchQuery(analysis);
      log.debug("벡터 검색 쿼리: {}", searchQuery);

      // 2. 회원 조회 후 개인화 추천 또는 일반 추천
      Optional<Member> memberOpt = memberRepository.findByDiscordId(discordId);
      List<Stream> recommend;

      if (memberOpt.isPresent()) {
        log.debug("개인화 추천 적용 - member: {}", memberOpt.get().getUuid());
        recommend = vectorRecommendService.recommend(searchQuery, memberOpt.get(), RECOMMENDED_NUM);
      } else {
        log.debug("비회원 - 일반 추천 적용");
        recommend = vectorRecommendService.recommend(searchQuery, RECOMMENDED_NUM);
      }

      // 3. 결과 응답
      if (recommend.isEmpty()) {
        publishResponse(channel, "아쉽게도 지금은 조건에 맞는 방송이 없어요. 다른 키워드로 다시 시도해보세요! 🔍");
        logElapsedTime(startTime, "추천 결과 없음");
      } else {
        // 각 방송을 개별 메시지 + 좋아요 버튼으로 전송
        for (Stream stream : recommend) {
          sendStreamWithLikeButton(channel, stream);
        }
        logElapsedTime(startTime, "추천 완료");
      }
    } catch (Exception e) {
      log.error("벡터 추천 중 오류 발생: {}", e.getMessage(), e);
      publishResponse(channel, "죄송해요, 지금은 응답을 드리기 어려워요. 잠시 후 다시 시도해주세요! 🙏");
      logElapsedTime(startTime, "추천 오류");
    }
  }

  /**
   * 방송 URL과 좋아요 버튼을 함께 전송합니다.
   *
   * @param channel 메시지를 보낼 채널
   * @param stream 방송 정보
   */
  private void sendStreamWithLikeButton(MessageChannelUnion channel, Stream stream) {
    String url = "https://chzzk.naver.com/live/" + stream.channelId();
    Button likeButton = Button.primary("like:" + stream.channelId(), "👍 좋아요");

    channel.sendMessage(url).addActionRow(likeButton).queue();
  }

  /** 게임/카테고리 키워드 반복 횟수 (임베딩 가중치 부여용) */
  private static final int KEYWORD_WEIGHT_MULTIPLIER = 5;

  /**
   * 분석 결과에서 벡터 검색용 쿼리를 생성합니다.
   *
   * <p>게임/카테고리 키워드에 높은 가중치를 부여하여 관련성을 높입니다:
   *
   * <ul>
   *   <li>첫 번째 키워드(게임명)는 5회 반복하여 가중치 부여
   *   <li>나머지 키워드와 semantic_tags는 1회씩 포함
   * </ul>
   *
   * <p>예: keywords=["롤", "여자"], tags=["실력방송"] → "롤 롤 롤 롤 롤 여자 실력방송"
   */
  private String buildSearchQuery(UserMessageAnalysisResult analysis) {
    List<String> queryParts = new ArrayList<>();

    // 첫 번째 키워드(게임명/카테고리)에 가중치 부여 (5회 반복)
    if (analysis.hasKeywords()) {
      List<String> keywords = analysis.getKeywords();
      String primaryKeyword = keywords.getFirst();

      // 게임명/카테고리를 5번 반복하여 임베딩에서 가중치 부여
      for (int i = 0; i < KEYWORD_WEIGHT_MULTIPLIER; i++) {
        queryParts.add(primaryKeyword);
      }

      // 나머지 키워드 추가 (성별, 시청자 조건 등)
      for (int i = 1; i < keywords.size(); i++) {
        queryParts.add(keywords.get(i));
      }
    }

    // semantic_tags 추가 (플레이 스타일, 분위기 등)
    if (analysis.hasSemanticTags()) {
      queryParts.addAll(analysis.getSemanticTags());
    }

    return String.join(" ", queryParts);
  }

  private void publishResponse(MessageChannelUnion channel, String message) {
    AiMessageResponseReceivedEvent responseEvent =
        new AiMessageResponseReceivedEvent(channel.getIdLong(), message);
    eventPublisher.publishEvent(responseEvent);
  }
}
