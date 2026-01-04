package com.vatti.chzscout.backend.discord.application;

import com.vatti.chzscout.backend.stream.domain.EnrichedStreamDto;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.User;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Discord DM 발송 서비스.
 *
 * <p>유저의 Discord ID를 통해 Private Channel을 열고 메시지를 발송합니다. DM 발송은 봇과 유저가 공통 서버를 공유해야 가능합니다.
 */
@Service
@Profile("!test")
@Slf4j
@RequiredArgsConstructor
public class DiscordDmService {

  private final JDA jda;

  private static String BASIC_LIVE = "https://chzzk.naver.com/live/";

  /**
   * 유저에게 방송 알림 DM을 발송합니다.
   *
   * @param discordId 유저의 Discord ID
   * @param streams 알림할 방송 목록
   * @return 발송 성공 여부를 담은 CompletableFuture
   */
  public CompletableFuture<Boolean> sendNotification(
      String discordId, Set<EnrichedStreamDto> streams) {

    CompletableFuture<Boolean> result = new CompletableFuture<>();

    jda.retrieveUserById(discordId)
        .queue(
            user -> sendDmToUser(user, streams, result),
            error -> {
              log.warn("Discord 유저 조회 실패: discordId={}, error={}", discordId, error.getMessage());
              result.complete(false);
            });

    return result;
  }

  private void sendDmToUser(
      User user, Set<EnrichedStreamDto> streams, CompletableFuture<Boolean> result) {

    user.openPrivateChannel()
        .queue(
            channel -> {
              String message = buildNotificationMessage(streams);
              channel
                  .sendMessage(message)
                  .queue(
                      success -> {
                        log.info("DM 발송 성공: userId={}", user.getId());
                        result.complete(true);
                      },
                      error -> {
                        log.warn("DM 발송 실패: userId={}, error={}", user.getId(), error.getMessage());
                        result.complete(false);
                      });
            },
            error -> {
              log.warn(
                  "Private Channel 열기 실패: userId={}, error={}", user.getId(), error.getMessage());
              result.complete(false);
            });
  }

  private String buildNotificationMessage(Set<EnrichedStreamDto> streams) {
    StringBuilder message = new StringBuilder("다음 방송을 추천해드려요!\n");
    for (EnrichedStreamDto stream : streams) {
      message.append(BASIC_LIVE).append(stream.channelId()).append("\n");
    }
    return message.toString();
  }
}
