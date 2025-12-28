package com.vatti.chzscout.backend.discord.presentation.listener;

import com.vatti.chzscout.backend.ai.application.AiChatService;
import com.vatti.chzscout.backend.ai.domain.dto.UserMessageAnalysisResult;
import com.vatti.chzscout.backend.ai.domain.event.AiMessageResponseReceivedEvent;
import com.vatti.chzscout.backend.stream.application.service.StreamRecommendationService;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
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
  private final AiChatService aiChatService;
  private final StreamRecommendationService streamRecommendationService;

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

    if (content.length() < MIN_LENGTH) {
      channel.sendMessage("메시지가 너무 짧아요! 2자 이상 입력해주세요 ✍️").queue();
      return;
    }

    if (content.length() > MAX_LENGTH) {
      channel.sendMessage("메시지가 너무 길어요! 500자 이하로 입력해주세요 📝").queue();
      return;
    }

    try {
      UserMessageAnalysisResult analysisResult = aiChatService.analyzeUserMessage(content);
      handleAnalysisResult(channel, analysisResult);
    } catch (Exception e) {
      log.error("메시지 처리 중 오류 발생: {}", e.getMessage(), e);
      channel.sendMessage("죄송해요, 지금은 응답을 드리기 어려워요. 잠시 후 다시 시도해주세요! 🙏").queue();
    }
  }

  private void handleAnalysisResult(MessageChannelUnion channel, UserMessageAnalysisResult result) {
    if (result.isRecommendationRequest()) {
      // 의미 태그와 키워드를 합쳐서 검색
      List<String> allTags = combineTagsAndKeywords(result);
      log.debug(
          "추천 검색 태그 - semanticTags: {}, keywords: {}",
          result.getSemanticTags(),
          result.getKeywords());

      String recommendation =
          streamRecommendationService.recommend(allTags).stream()
              .map(stream -> "https://chzzk.naver.com/live/" + stream.channelId())
              .collect(Collectors.joining("\n"));

      if (recommendation.isEmpty()) {
        publishResponse(channel, "아쉽게도 지금은 조건에 맞는 방송이 없어요. 다른 키워드로 다시 시도해보세요! 🔍");
      } else {
        publishResponse(channel, recommendation);
      }
    } else if (result.hasDirectReply()) {
      // search 또는 other: GPT가 생성한 reply를 그대로 전송
      publishResponse(channel, result.getReply());
    }
  }

  private List<String> combineTagsAndKeywords(UserMessageAnalysisResult result) {
    List<String> combined = new ArrayList<>();
    if (result.hasSemanticTags()) {
      combined.addAll(result.getSemanticTags());
    }
    if (result.hasKeywords()) {
      combined.addAll(result.getKeywords());
    }
    return combined;
  }

  private void publishResponse(MessageChannelUnion channel, String message) {
    AiMessageResponseReceivedEvent responseEvent =
        new AiMessageResponseReceivedEvent(channel.getIdLong(), message);
    eventPublisher.publishEvent(responseEvent);
  }
}
