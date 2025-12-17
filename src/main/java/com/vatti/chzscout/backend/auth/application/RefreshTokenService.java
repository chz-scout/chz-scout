package com.vatti.chzscout.backend.auth.application;

import com.vatti.chzscout.backend.auth.domain.repository.RefreshTokenRepository;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.apache.coyote.BadRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Refresh Token의 저장/조회/삭제를 담당. */
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

  private final RefreshTokenRepository refreshTokenRepository;
  private final JwtTokenProvider jwtTokenProvider;

  @Value("${jwt.refresh-token-expiration}")
  private Long refreshTokenExpiration;

  /** jti를 key로 Refresh Token 저장 (TTL 설정). */
  public void save(String refreshToken) {
    String jti = jwtTokenProvider.getJti(refreshToken);
    refreshTokenRepository.save(jti, refreshToken, Duration.ofMillis(refreshTokenExpiration));
  }

  /** jti로 Refresh Token 조회. */
  String findByJti(String jti) {
    return refreshTokenRepository.findByJti(jti);
  }

  /** jti로 Refresh Token 삭제 (로그아웃). */
  public void deleteByJti(String jti) {
    refreshTokenRepository.deleteByJti(jti);
  }

  /**
   * Refresh Token으로 새 Access Token 발급
   *
   * @param refreshToken 클라이언트가 전달한 Refresh Token
   * @return 새로 발급된 Access Token
   */
  public String reissue(String refreshToken) throws BadRequestException {
    if (!jwtTokenProvider.validateToken(refreshToken)) {
      throw new BadRequestException("Invalid refresh token");
    }
    String jti = jwtTokenProvider.getJti(refreshToken);
    if (findByJti(jti) == null) {
      throw new BadRequestException("No refresh token found");
    }

    String uuid = jwtTokenProvider.getUuid(refreshToken);

    return jwtTokenProvider.generateAccessToken(uuid, "USER");
  }
}
