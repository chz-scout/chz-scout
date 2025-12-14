package com.vatti.chzscout.backend.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class JwtTokenProviderTest {

  private JwtTokenProvider jwtTokenProvider;

  private final String uuid = "test-uuid-12345";
  private final String role = "USER";
  // HS256은 최소 32바이트(256비트) 키 필요
  private final String secretKey = "test-secret-key-for-jwt-token-32bytes!";
  private final Long accessTokenExpiration = 3600000L; // 1시간
  private final Long refreshTokenExpiration = 604800000L; // 7일

  @BeforeEach
  void setUp() {
    jwtTokenProvider = new JwtTokenProvider();
    // ReflectionTestUtils로 @Value 필드 주입
    ReflectionTestUtils.setField(jwtTokenProvider, "secretKey", secretKey);
    ReflectionTestUtils.setField(jwtTokenProvider, "accessTokenExpiration", accessTokenExpiration);
    ReflectionTestUtils.setField(
        jwtTokenProvider, "refreshTokenExpiration", refreshTokenExpiration);
  }

  private SecretKey getSigningKey() {
    return Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
  }

  @Nested
  @DisplayName("accessToken 생성")
  class generateAccessToken {
    @Test
    @DisplayName("uuid, role을 받아서 accessToken을 생성한다")
    void generateAccessToken_WithAllFields_Success() {
      // given

      // when
      String accessToken = jwtTokenProvider.generateAccessToken(uuid, role);

      // then
      assertTrue(jwtTokenProvider.validateToken(accessToken));
      assertThat(jwtTokenProvider.getUuid(accessToken)).isEqualTo(uuid);
      assertThat(jwtTokenProvider.getRole(accessToken)).isEqualTo(role);
    }

    @Test
    @DisplayName("uuid가 null이어도 토큰 생성은 성공한다 (클레임에 null 저장)")
    void generateAccessToken_WithNullUuid_Success() {
      // given & when
      String accessToken = jwtTokenProvider.generateAccessToken(null, role);

      // then
      assertTrue(jwtTokenProvider.validateToken(accessToken));
      assertThat(jwtTokenProvider.getUuid(accessToken)).isNull();
      assertThat(jwtTokenProvider.getRole(accessToken)).isEqualTo(role);
    }
  }

  @Nested
  @DisplayName("refreshToken 생성")
  class generateRefreshToken {
    @Test
    @DisplayName("uuid, refreshToken을 생성한다")
    void generateRefreshToken_WithAllFields_Success() {
      // given

      // when
      String refreshToken = jwtTokenProvider.generateRefreshToken(uuid);

      // then
      assertTrue(jwtTokenProvider.validateToken(refreshToken));
      assertThat(jwtTokenProvider.getUuid(refreshToken)).isEqualTo(uuid);
      assertNotNull(jwtTokenProvider.getJti(refreshToken));
    }

    @Test
    @DisplayName("uuid가 null이어도 토큰 생성은 성공한다")
    void generateRefreshToken_WithNullUuid_Success() {
      // given & when
      String refreshToken = jwtTokenProvider.generateRefreshToken(null);

      // then
      assertTrue(jwtTokenProvider.validateToken(refreshToken));
      assertThat(jwtTokenProvider.getUuid(refreshToken)).isNull();
      assertNotNull(jwtTokenProvider.getJti(refreshToken));
    }

    @Test
    @DisplayName("매번 다른 jti가 생성된다")
    void generateRefreshToken_UniqueJti() {
      // given & when
      String refreshToken1 = jwtTokenProvider.generateRefreshToken(uuid);
      String refreshToken2 = jwtTokenProvider.generateRefreshToken(uuid);

      // then
      String jti1 = jwtTokenProvider.getJti(refreshToken1);
      String jti2 = jwtTokenProvider.getJti(refreshToken2);
      assertThat(jti1).isNotEqualTo(jti2);
    }
  }

  @Nested
  @DisplayName("토큰 유효성 검사 테스트")
  class validateToken {
    @Test
    @DisplayName("정상적인 토큰은 true를 리턴한다")
    void validateNormalToken() {
      // given
      String accessToken = jwtTokenProvider.generateAccessToken(uuid, role);
      String refreshToken = jwtTokenProvider.generateRefreshToken(uuid);

      // when

      // then
      assertTrue(jwtTokenProvider.validateToken(accessToken));
      assertTrue(jwtTokenProvider.validateToken(refreshToken));
    }

    @Test
    @DisplayName("기간이 만료된 토큰은 false를 리턴한다")
    void validateExpiredToken() {
      // given
      String expiredToken =
          Jwts.builder()
              .claim("uuid", uuid)
              .claim("role", role)
              .issuedAt(new Date(System.currentTimeMillis()))
              .expiration(new Date(System.currentTimeMillis() - 1000000))
              .signWith(getSigningKey(), Jwts.SIG.HS256)
              .compact();

      // when

      // then
      assertFalse(jwtTokenProvider.validateToken(expiredToken));
    }
  }

  @Nested
  @DisplayName("토큰에서 클레임 추출 테스트")
  class GetClaimsTest {

    @Test
    @DisplayName("getUuid - accessToken에서 uuid 추출")
    void getUuid_FromAccessToken_Success() {
      // given
      String accessToken = jwtTokenProvider.generateAccessToken(uuid, role);

      // when
      String extractedUuid = jwtTokenProvider.getUuid(accessToken);

      // then
      assertThat(extractedUuid).isEqualTo(uuid);
    }

    @Test
    @DisplayName("getRole - accessToken에서 role 추출")
    void getRole_FromAccessToken_Success() {
      // given
      String accessToken = jwtTokenProvider.generateAccessToken(uuid, role);

      // when
      String extractedRole = jwtTokenProvider.getRole(accessToken);

      // then
      assertThat(extractedRole).isEqualTo(role);
    }

    @Test
    @DisplayName("getJti - refreshToken에서 jti 추출")
    void getJti_FromRefreshToken_Success() {
      // given
      String refreshToken = jwtTokenProvider.generateRefreshToken(uuid);

      // when
      String extractedJti = jwtTokenProvider.getJti(refreshToken);

      // then
      assertThat(extractedJti).isNotNull();
      assertThat(extractedJti).isNotEmpty();
    }

    @Test
    @DisplayName("잘못된 토큰에서 클레임 추출 시 예외 발생")
    void getClaims_WithInvalidToken_ThrowsException() {
      // given
      String invalidToken = "invalid.token.here";

      // when & then
      assertThatThrownBy(() -> jwtTokenProvider.getUuid(invalidToken))
          .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("다른 키로 서명된 토큰은 검증 실패")
    void validateToken_WithDifferentKey_ReturnsFalse() {
      // given
      String differentKey = "different-secret-key-32bytes-long!!";
      SecretKey otherKey = Keys.hmacShaKeyFor(differentKey.getBytes(StandardCharsets.UTF_8));

      String tokenWithDifferentKey =
          Jwts.builder()
              .claim("uuid", uuid)
              .claim("role", role)
              .issuedAt(new Date(System.currentTimeMillis()))
              .expiration(new Date(System.currentTimeMillis() + 3600000))
              .signWith(otherKey, Jwts.SIG.HS256)
              .compact();

      // when & then
      assertFalse(jwtTokenProvider.validateToken(tokenWithDifferentKey));
    }
  }
}
