package com.vatti.chzscout.backend.stream.infrastructure.scheduler;

import com.vatti.chzscout.backend.stream.application.StreamCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
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
      streamCacheService.refreshLiveStreams();
      log.info("Completed scheduled live streams cache refresh");
    } catch (Exception e) {
      log.error("Failed to refresh live streams cache", e);
    }
  }
}
