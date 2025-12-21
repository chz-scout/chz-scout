package com.vatti.chzscout.backend.stream.domain.event;

/**
 * 스트림 캐시 갱신 완료 이벤트.
 *
 * <p>StreamCacheScheduler가 생방송 캐시 갱신을 완료했을 때 발행됩니다.
 */
public record StreamCacheRefreshedEvent() {}
