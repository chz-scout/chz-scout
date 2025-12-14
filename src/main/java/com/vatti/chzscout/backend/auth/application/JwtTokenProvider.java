package com.vatti.chzscout.backend.auth.application;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** JWT 토큰 생성 및 검증을 담당하는 Provider. */
@Component
public class JwtTokenProvider {

  @Value("${jwt.secret}")
  private String secretKey;

  @Value("${jwt.access-token-expiration}")
  private Long accessTokenExpiration;

  @Value("${jwt.refresh-token-expiration}")
  private Long refreshTokenExpiration;

  /** Access Token 생성. uuid와 role을 클레임에 포함. */
  public String generateAccessToken(String uuid, String role) {
    return Jwts.builder()
        .claim("uuid", uuid)
        .claim("role", role)
        .issuedAt(new Date(System.currentTimeMillis()))
        .expiration(new Date(System.currentTimeMillis() + accessTokenExpiration))
        .signWith(getSigningKey(), Jwts.SIG.HS256)
        .compact();
  }

  /** Refresh Token 생성. jti로 개별 토큰 식별 가능. */
  public String generateRefreshToken(String uuid) {
    return Jwts.builder()
        .claim("uuid", uuid)
        .claim("jti", UUID.randomUUID().toString())
        .issuedAt(new Date(System.currentTimeMillis()))
        .expiration(new Date(System.currentTimeMillis() + refreshTokenExpiration))
        .signWith(getSigningKey(), Jwts.SIG.HS256)
        .compact();
  }

  /** 토큰 검증 후 uuid 반환. 서명/만료 검증은 getClaims에서 자동 수행. */
  public Boolean validateToken(String token) {
    try {
      getClaims(token);
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  /** 토큰에서 uuid 추출. */
  public String getUuid(String token) {
    return getClaims(token).get("uuid", String.class);
  }

  /** 토큰에서 jti 추출. */
  public String getJti(String token) {
    return getClaims(token).get("jti", String.class);
  }

  /** 토큰에서 role 추출. */
  public String getRole(String token) {
    return getClaims(token).get("role", String.class);
  }

  /** 토큰 파싱 및 검증. 서명 불일치/만료 시 예외 발생. */
  public Claims getClaims(String token) {
    return Jwts.parser().verifyWith(getSigningKey()).build().parseSignedClaims(token).getPayload();
  }

  private SecretKey getSigningKey() {
    return Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
  }
}
