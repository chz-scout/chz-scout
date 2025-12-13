package com.vatti.chzscout.backend.auth.domain.dto;

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
    String accessToken, String tokenType, Integer expiresIn, String refreshToken, String scope) {}
