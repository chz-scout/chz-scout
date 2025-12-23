package com.vatti.chzscout.backend.ai.domain.event;

/**
 * AI 응답 완료 이벤트.
 *
 * @param channelId Discord 채널 ID (응답을 보낼 대상)
 * @param response AI가 생성한 응답 메시지
 */
public record AiMessageResponseReceivedEvent(Long channelId, String response) {}
