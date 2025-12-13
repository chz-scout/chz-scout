package com.vatti.chzscout.backend.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.time.Duration;
import org.apache.coyote.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

  private RefreshTokenService refreshTokenService;

  @Mock private StringRedisTemplate stringRedisTemplate;
  @Mock private JwtTokenProvider jwtTokenProvider;
  @Mock private ValueOperations<String, String> valueOperations;

  private final String testJti = "test-jti-12345";
  private final String testUuid = "test-uuid-67890";
  private final String testRefreshToken = "test.refresh.token";
  private final Long refreshTokenExpiration = 604800000L; // 7일

  @BeforeEach
  void setUp() {
    refreshTokenService = new RefreshTokenService(stringRedisTemplate, jwtTokenProvider);
    ReflectionTestUtils.setField(
        refreshTokenService, "refreshTokenExpiration", refreshTokenExpiration);
  }

  @Nested
  @DisplayName("save 메서드")
  class SaveTest {

    @Test
    @DisplayName("Refresh Token을 Redis에 저장한다")
    void save_Success() {
      // given
      given(jwtTokenProvider.getJti(testRefreshToken)).willReturn(testJti);
      given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);

      // when
      refreshTokenService.save(testRefreshToken);

      // then
      then(valueOperations)
          .should()
          .set(
              eq("auth:refresh:" + testJti),
              eq(testRefreshToken),
              eq(Duration.ofMillis(refreshTokenExpiration)));
    }
  }

  @Nested
  @DisplayName("findByJti 메서드")
  class FindByJtiTest {

    @Test
    @DisplayName("jti로 Refresh Token을 조회한다")
    void findByJti_Success() {
      // given
      given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
      given(valueOperations.get("auth:refresh:" + testJti)).willReturn(testRefreshToken);

      // when
      String result = refreshTokenService.findByJti(testJti);

      // then
      assertThat(result).isEqualTo(testRefreshToken);
    }

    @Test
    @DisplayName("존재하지 않는 jti로 조회하면 null 반환")
    void findByJti_NotFound_ReturnsNull() {
      // given
      given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
      given(valueOperations.get("auth:refresh:" + testJti)).willReturn(null);

      // when
      String result = refreshTokenService.findByJti(testJti);

      // then
      assertThat(result).isNull();
    }
  }

  @Nested
  @DisplayName("deleteByJti 메서드")
  class DeleteByJtiTest {

    @Test
    @DisplayName("jti로 Refresh Token을 삭제한다")
    void deleteByJti_Success() {
      // given

      // when
      refreshTokenService.deleteByJti(testJti);

      // then
      then(stringRedisTemplate).should().delete("auth:refresh:" + testJti);
    }
  }

  @Nested
  @DisplayName("reissue 메서드")
  class ReissueTest {

    @Test
    @DisplayName("유효한 Refresh Token으로 새 Access Token 발급 성공")
    void reissue_Success() throws BadRequestException {
      // given
      String expectedAccessToken = "new.access.token";

      given(jwtTokenProvider.validateToken(testRefreshToken)).willReturn(true);
      given(jwtTokenProvider.getJti(testRefreshToken)).willReturn(testJti);
      given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
      given(valueOperations.get("auth:refresh:" + testJti)).willReturn(testRefreshToken);
      given(jwtTokenProvider.getUuid(testRefreshToken)).willReturn(testUuid);
      given(jwtTokenProvider.generateAccessToken(testUuid, "USER")).willReturn(expectedAccessToken);

      // when
      String result = refreshTokenService.reissue(testRefreshToken);

      // then
      assertThat(result).isEqualTo(expectedAccessToken);
    }

    @Test
    @DisplayName("유효하지 않은 Refresh Token으로 재발급 시 예외 발생")
    void reissue_InvalidToken_ThrowsException() {
      // given
      given(jwtTokenProvider.validateToken(testRefreshToken)).willReturn(false);

      // when & then
      assertThatThrownBy(() -> refreshTokenService.reissue(testRefreshToken))
          .isInstanceOf(BadRequestException.class)
          .hasMessage("Invalid refresh token");
    }

    @Test
    @DisplayName("Redis에 저장되지 않은 Refresh Token으로 재발급 시 예외 발생")
    void reissue_TokenNotFoundInRedis_ThrowsException() {
      // given
      given(jwtTokenProvider.validateToken(testRefreshToken)).willReturn(true);
      given(jwtTokenProvider.getJti(testRefreshToken)).willReturn(testJti);
      given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
      given(valueOperations.get("auth:refresh:" + testJti)).willReturn(null);

      // when & then
      assertThatThrownBy(() -> refreshTokenService.reissue(testRefreshToken))
          .isInstanceOf(BadRequestException.class)
          .hasMessage("No refresh token found");
    }
  }
}
