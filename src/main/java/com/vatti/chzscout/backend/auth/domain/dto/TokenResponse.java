package com.vatti.chzscout.backend.auth.domain.dto;

/**
 * 토큰 발급 응답 DTO.
 *
 * @param accessToken JWT Access Token
 * @param refreshToken JWT Refresh Token
 */
public record TokenResponse(String accessToken, String refreshToken) {}
