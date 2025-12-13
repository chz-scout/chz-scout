package com.vatti.chzscout.backend.auth.domain.dto;

/**
 * Discord OAuth 2.0 콜백 요청 DTO.
 *
 * @param code Discord에서 발급한 인가 코드 (access_token으로 교환)
 * @param state CSRF 방지용 상태값
 */
public record OAuthCallbackRequest(String code, String state) {}
