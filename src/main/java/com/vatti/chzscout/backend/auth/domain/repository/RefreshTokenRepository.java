package com.vatti.chzscout.backend.auth.domain.repository;

import java.time.Duration;

/** Refresh Token 저장소 인터페이스. */
public interface RefreshTokenRepository {

  /** jti를 key로 Refresh Token 저장 (TTL 설정). */
  void save(String jti, String refreshToken, Duration ttl);

  /** jti로 Refresh Token 조회. */
  String findByJti(String jti);

  /** jti로 Refresh Token 삭제 (로그아웃). */
  void deleteByJti(String jti);
}
