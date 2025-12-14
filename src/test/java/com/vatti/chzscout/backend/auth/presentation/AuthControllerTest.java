package com.vatti.chzscout.backend.auth.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willDoNothing;

import com.vatti.chzscout.backend.auth.application.AuthService;
import com.vatti.chzscout.backend.auth.application.DiscordOAuthClient;
import com.vatti.chzscout.backend.auth.application.JwtTokenProvider;
import com.vatti.chzscout.backend.auth.application.RefreshTokenService;
import com.vatti.chzscout.backend.auth.domain.dto.DiscordTokenResponse;
import com.vatti.chzscout.backend.auth.domain.dto.DiscordUserProfile;
import com.vatti.chzscout.backend.auth.domain.dto.OAuthCallbackRequest;
import com.vatti.chzscout.backend.auth.domain.dto.TokenResponse;
import com.vatti.chzscout.backend.common.response.ApiResponse;
import com.vatti.chzscout.backend.member.domain.entity.Member;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.apache.coyote.BadRequestException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

  @InjectMocks private AuthController authController;

  @Mock private DiscordOAuthClient discordOAuthClient;
  @Mock private JwtTokenProvider jwtTokenProvider;
  @Mock private RefreshTokenService refreshTokenService;
  @Mock private AuthService authService;
  @Mock private HttpServletResponse httpServletResponse;

  @Nested
  @DisplayName("redirectToDiscord 메서드")
  class RedirectToDiscordTest {

    @Test
    @DisplayName("Discord OAuth 페이지로 리다이렉트")
    void redirectToDiscord_Success() throws IOException {
      // given
      String oauthUrl = "https://discord.com/oauth2/authorize";
      ReflectionTestUtils.setField(authController, "discordOAuthUrl", oauthUrl);

      // when
      authController.redirectToDiscord(httpServletResponse);

      // then
      then(httpServletResponse).should().sendRedirect(oauthUrl);
    }
  }

  @Nested
  @DisplayName("handleCallback 메서드")
  class HandleCallbackTest {

    @Test
    @DisplayName("Discord OAuth 콜백 처리 성공")
    void handleCallback_Success() {
      // given
      OAuthCallbackRequest request = new OAuthCallbackRequest("auth-code", "guild123");
      DiscordTokenResponse tokenResponse =
          new DiscordTokenResponse("access-token", "Bearer", 604800, "refresh-token", "identify");
      DiscordUserProfile profile =
          new DiscordUserProfile(
              "discord123", "testuser", "Test User", "avatar", "test@example.com", true);
      Member member = Member.create("discord123", "testuser", "test@example.com");

      given(discordOAuthClient.exchangeToken("auth-code")).willReturn(tokenResponse);
      given(discordOAuthClient.getUserProfile("access-token")).willReturn(profile);
      given(authService.findOrCreateMember("discord123", "testuser", "test@example.com"))
          .willReturn(member);
      given(jwtTokenProvider.generateAccessToken(anyString(), anyString()))
          .willReturn("jwt-access-token");
      given(jwtTokenProvider.generateRefreshToken(anyString())).willReturn("jwt-refresh-token");

      // when
      ResponseEntity<ApiResponse<TokenResponse>> result = authController.handleCallback(request);

      // then
      assertThat(result.getStatusCode().is2xxSuccessful()).isTrue();
      assertThat(result.getBody()).isNotNull();
      assertThat(result.getBody().isSuccess()).isTrue();
      assertThat(result.getBody().getData().accessToken()).isEqualTo("jwt-access-token");
      assertThat(result.getBody().getData().refreshToken()).isEqualTo("jwt-refresh-token");
      then(refreshTokenService).should().save("jwt-refresh-token");
    }
  }

  @Nested
  @DisplayName("reissueToken 메서드")
  class ReissueTokenTest {

    @Test
    @DisplayName("토큰 재발급 성공")
    void reissueToken_Success() throws BadRequestException {
      // given
      given(refreshTokenService.reissue("valid-refresh-token")).willReturn("new-access-token");

      // when
      ResponseEntity<ApiResponse<TokenResponse>> result =
          authController.reissueToken("Bearer valid-refresh-token");

      // then
      assertThat(result.getStatusCode().is2xxSuccessful()).isTrue();
      assertThat(result.getBody().getData().accessToken()).isEqualTo("new-access-token");
    }

    @Test
    @DisplayName("유효하지 않은 토큰으로 재발급 시 예외 발생")
    void reissueToken_InvalidToken_ThrowsException() throws BadRequestException {
      // given
      given(refreshTokenService.reissue("invalid-token"))
          .willThrow(new BadRequestException("Invalid refresh token"));

      // when & then
      assertThatThrownBy(() -> authController.reissueToken("Bearer invalid-token"))
          .isInstanceOf(BadRequestException.class);
    }
  }

  @Nested
  @DisplayName("logout 메서드")
  class LogoutTest {

    @Test
    @DisplayName("로그아웃 성공")
    void logout_Success() throws BadRequestException {
      // given
      willDoNothing().given(authService).logout("valid-refresh-token");

      // when
      authController.logout("Bearer valid-refresh-token");

      // then
      then(authService).should().logout("valid-refresh-token");
    }
  }
}
