package com.vatti.chzscout.backend.stream.infrastructure.scheduler;

import com.vatti.chzscout.backend.ai.application.AiChatService;
import com.vatti.chzscout.backend.ai.domain.dto.StreamTagResult;
import com.vatti.chzscout.backend.ai.prompt.TagExtractionPrompts;
import com.vatti.chzscout.backend.stream.application.StreamCacheService;
import com.vatti.chzscout.backend.stream.domain.AllFieldLiveDto;
import com.vatti.chzscout.backend.stream.domain.EnrichedStreamDto;
import com.vatti.chzscout.backend.stream.domain.event.StreamCacheRefreshedEvent;
import com.vatti.chzscout.backend.stream.infrastructure.redis.StreamRedisStore;
import com.vatti.chzscout.backend.tag.application.usecase.TagUseCase;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 생방송 캐시를 주기적으로 갱신하는 스케줄러. */
@Component
@Profile("!test")
@Slf4j
@RequiredArgsConstructor
public class StreamCacheScheduler {

  private final StreamCacheService streamCacheService;
  private final TagUseCase tagUseCase;
  private final ApplicationEventPublisher eventPublisher;
  private final AiChatService aiChatService;
  private final StreamRedisStore streamRedisStore;

  /** 애플리케이션 시작 시 즉시 캐시 초기화. */
  @EventListener(ApplicationReadyEvent.class)
  public void onApplicationReady() {
    log.info("Application ready, initializing live streams cache");
    refreshLiveStreamsCache();
  }

  /** 5분마다 치지직 API를 호출하여 생방송 목록 캐시 갱신. */
  @Scheduled(fixedRate = 300_000, initialDelay = 300_000)
  public void refreshLiveStreamsCache() {
    log.info("Starting scheduled live streams cache refresh");
    try {
      // 1. 치지직 API에서 생방송 목록 가져오기
      List<AllFieldLiveDto> streams = streamCacheService.fetchLiveStreams();
      log.info("Fetched {} live streams from API", streams.size());

      if (streams.isEmpty()) {
        log.warn("No streams fetched, skipping cache refresh");
        return;
      }

      // 2. 태그 DB 저장 (자동완성용)
      tagUseCase.extractAndSaveTag(streams);

      // 3. 변경 감지 → AI 태그 추출 → 최종 리스트 구성
      Set<String> changedIds = streamRedisStore.detectChanges(streams).getAllChangedIds();
      Map<String, EnrichedStreamDto> existingMap = getExistingEnrichedMap();
      Map<String, EnrichedStreamDto> newEnrichedMap = extractAiTagsForChanged(streams, changedIds);

      List<EnrichedStreamDto> finalEnriched =
          buildFinalEnrichedList(streams, changedIds, existingMap, newEnrichedMap);

      // 4. Redis 저장 및 이벤트 발행
      streamRedisStore.saveEnrichedStreams(finalEnriched);
      log.info("Redis에 {}개 Enriched 방송 저장 완료", finalEnriched.size());

      eventPublisher.publishEvent(new StreamCacheRefreshedEvent());
    } catch (Exception e) {
      log.error("Failed to refresh live streams cache", e);
    }
  }

  /** 기존 Redis에 저장된 Enriched 데이터를 Map으로 조회. */
  private Map<String, EnrichedStreamDto> getExistingEnrichedMap() {
    return streamRedisStore.findEnrichedStreams().stream()
        .collect(Collectors.toMap(EnrichedStreamDto::channelId, Function.identity()));
  }

  /**
   * 변경된 방송만 AI 태그 추출하여 Map으로 반환.
   *
   * @return channelId → EnrichedStreamDto 맵 (변경된 방송만)
   */
  private Map<String, EnrichedStreamDto> extractAiTagsForChanged(
      List<AllFieldLiveDto> streams, Set<String> changedIds) {
    if (changedIds.isEmpty()) {
      return Map.of();
    }

    // 변경된 방송만 필터링
    List<AllFieldLiveDto> changedStreams =
        streams.stream().filter(s -> changedIds.contains(s.channelId())).toList();

    // AI API 호출
    List<TagExtractionPrompts.StreamInput> inputList =
        changedStreams.stream()
            .map(
                dto ->
                    new TagExtractionPrompts.StreamInput(
                        dto.channelId(), dto.liveTitle(), dto.liveCategoryValue(), dto.tags()))
            .toList();

    List<StreamTagResult> tagResults = aiChatService.extractStreamTagsBatch(inputList);
    Map<String, StreamTagResult> tagResultMap =
        tagResults.stream()
            .collect(
                Collectors.toMap(
                    StreamTagResult::channelId,
                    Function.identity(),
                    (existing, duplicate) -> existing)); // 중복 시 첫 번째 값 유지

    // EnrichedStreamDto 생성
    // - AI 결과 있으면: 원본 + AI 태그
    // - AI 결과 없으면: 원본 태그만 (Fallback)
    Map<String, EnrichedStreamDto> result =
        changedStreams.stream()
            .collect(
                Collectors.toMap(
                    AllFieldLiveDto::channelId,
                    dto -> {
                      StreamTagResult tagResult = tagResultMap.get(dto.channelId());
                      return tagResult != null
                          ? EnrichedStreamDto.from(dto, tagResult)
                          : EnrichedStreamDto.fromWithoutAi(dto);
                    }));

    log.info("AI 태그 추출 완료 - {}개 방송 처리", changedStreams.size());
    return result;
  }

  /**
   * 최종 Enriched 리스트 구성.
   *
   * <p>현재 방송만 순회하므로 종료된 방송은 자동 제외됨.
   */
  private List<EnrichedStreamDto> buildFinalEnrichedList(
      List<AllFieldLiveDto> streams,
      Set<String> changedIds,
      Map<String, EnrichedStreamDto> existingMap,
      Map<String, EnrichedStreamDto> newEnrichedMap) {

    List<EnrichedStreamDto> result = new ArrayList<>();

    for (AllFieldLiveDto stream : streams) {
      String channelId = stream.channelId();

      if (changedIds.contains(channelId)) {
        // 변경됨 → 새로 추출한 것 사용
        EnrichedStreamDto newEnriched = newEnrichedMap.get(channelId);
        if (newEnriched != null) {
          result.add(newEnriched);
        }
      } else if (existingMap.containsKey(channelId)) {
        // 변경 안 됨 → 기존 것 유지
        result.add(existingMap.get(channelId));
      } else {
        // 예외 케이스 → 원본으로 저장
        result.add(EnrichedStreamDto.fromWithoutAi(stream));
      }
    }

    return result;
  }
}
