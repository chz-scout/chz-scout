package com.vatti.chzscout.backend.stream.domain.event;

import java.util.Set;

/**
 * 스트림 알림 트리거 이벤트.
 *
 * <p>스트림 캐시 갱신 완료 후 발행되며, memberTag와 현재 방송 태그를 대조하여 매칭되는 유저에게 알림을 발송하는 작업을 트리거합니다.
 *
 * @param changedChannelIds 신규 또는 변경된 방송의 channelId 집합
 */
public record StreamNotificationTriggerEvent(Set<String> changedChannelIds) {}
