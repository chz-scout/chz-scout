package com.vatti.chzscout.backend.auth.infrastructure.redis;

import com.vatti.chzscout.backend.auth.domain.repository.RefreshTokenRepository;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/** Redis 기반 Refresh Token 저장소 구현체. */
@Repository
@RequiredArgsConstructor
public class RefreshTokenRedisStore implements RefreshTokenRepository {

  private static final String KEY_PREFIX = "auth:refresh:";

  private final StringRedisTemplate stringRedisTemplate;

  @Override
  public void save(String jti, String refreshToken, Duration ttl) {
    stringRedisTemplate.opsForValue().set(generateKey(jti), refreshToken, ttl);
  }

  @Override
  public String findByJti(String jti) {
    return stringRedisTemplate.opsForValue().get(generateKey(jti));
  }

  @Override
  public void deleteByJti(String jti) {
    stringRedisTemplate.delete(generateKey(jti));
  }

  private String generateKey(String jti) {
    return KEY_PREFIX + jti;
  }
}
