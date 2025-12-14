package com.vatti.chzscout.backend.auth.domain.dto;

/**
 * Discord OAuth 2.0 콜백 요청 DTO.
 *
 * @param code Discord에서 발급한 인가 코드 (access_token으로 교환)
 * @param guildId 봇이 초대된 서버 ID (Bot 초대 시 함께 전달됨)
 */
public record OAuthCallbackRequest(String code, String guildId) {}
