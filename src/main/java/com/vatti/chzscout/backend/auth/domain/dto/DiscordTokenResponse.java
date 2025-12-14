package com.vatti.chzscout.backend.auth.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Discord OAuth 2.0 토큰 응답 DTO.
 *
 * @param accessToken 사용자 정보 조회에 사용하는 액세스 토큰
 * @param tokenType 토큰 타입 (Bearer)
 * @param expiresIn 토큰 만료 시간 (초)
 * @param refreshToken 액세스 토큰 갱신용 리프레시 토큰
 * @param scope 허용된 권한 범위
 */
public record DiscordTokenResponse(
    @JsonProperty("access_token") String accessToken,
    @JsonProperty("token_type") String tokenType,
    @JsonProperty("expires_in") Integer expiresIn,
    @JsonProperty("refresh_token") String refreshToken,
    @JsonProperty("scope") String scope) {}
