package com.vatti.chzscout.backend.tag.infrastructure.listener;

import com.vatti.chzscout.backend.stream.domain.event.StreamCacheRefreshedEvent;
import com.vatti.chzscout.backend.tag.application.usecase.TagUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 태그 자동완성 캐시 갱신 리스너.
 *
 * <p>StreamCacheRefreshedEvent를 수신하여 스트림 캐시 갱신 완료 후 태그 Redis 캐시를 갱신합니다.
 */
@Component
@Profile("!test")
@Slf4j
@RequiredArgsConstructor
public class TagCacheRefreshListener {

  private final TagUseCase tagUseCase;

  /**
   * 스트림 캐시 갱신 완료 이벤트를 수신하여 태그 Redis 캐시를 갱신합니다.
   *
   * <p>StreamCacheScheduler가 스트림 데이터 갱신을 완료하면 이 메서드가 호출되어 DB에 저장된 최신 태그를 Redis에 동기화합니다.
   */
  @EventListener(StreamCacheRefreshedEvent.class)
  public void onStreamCacheRefreshed() {
    log.info("Received StreamCacheRefreshedEvent, refreshing tag autocomplete cache");
    tagUseCase.refreshAutocompleteCache();
  }
}
