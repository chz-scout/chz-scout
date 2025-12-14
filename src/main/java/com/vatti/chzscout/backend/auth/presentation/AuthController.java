package com.vatti.chzscout.backend.auth.presentation;

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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.coyote.BadRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Discord OAuth 인증 및 토큰 관리 Controller. */
@Slf4j
@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
public class AuthController {

  private final DiscordOAuthClient discordOAuthClient;
  private final JwtTokenProvider jwtTokenProvider;
  private final RefreshTokenService refreshTokenService;
  private final AuthService authService;

  @Value("${discord.oauth.url}")
  private String discordOAuthUrl;

  /** Discord OAuth 로그인 페이지로 리다이렉트. */
  @GetMapping("/discord")
  public void redirectToDiscord(HttpServletResponse response) throws IOException {
    log.info("Discord OAuth 로그인 요청");
    response.sendRedirect(discordOAuthUrl);
  }

  /** Discord OAuth 콜백 처리. 토큰 발급. */
  @PostMapping("/discord/callback")
  public ResponseEntity<ApiResponse<TokenResponse>> handleCallback(
      @RequestBody OAuthCallbackRequest request) {
    log.info("Discord OAuth 콜백 수신: guildId={}", request.guildId());

    // Discord 토큰 교환
    DiscordTokenResponse discordToken = discordOAuthClient.exchangeToken(request.code());
    log.debug("Discord 토큰 교환 완료");

    // Discord 사용자 정보 조회
    DiscordUserProfile profile = discordOAuthClient.getUserProfile(discordToken.accessToken());
    log.info("Discord 로그인 성공: discordId={}, username={}", profile.id(), profile.username());

    // Member 조회 또는 생성
    Member member =
        authService.findOrCreateMember(profile.id(), profile.username(), profile.email());
    log.debug("Member 처리 완료: uuid={}", member.getUuid());

    // JWT 토큰 생성
    String accessToken = jwtTokenProvider.generateAccessToken(member.getUuid(), "USER");
    String refreshToken = jwtTokenProvider.generateRefreshToken(member.getUuid());

    // Refresh Token Redis 저장
    refreshTokenService.save(refreshToken);
    log.info("로그인 완료: uuid={}", member.getUuid());

    return ResponseEntity.ok(ApiResponse.success(new TokenResponse(accessToken, refreshToken)));
  }

  /** Access Token 재발급. */
  @PostMapping("/reissue")
  public ResponseEntity<ApiResponse<TokenResponse>> reissueToken(
      @RequestHeader("Authorization") String refreshTokenHeader) throws BadRequestException {
    log.info("토큰 재발급 요청");

    // "Bearer " 접두사 제거
    String refreshToken = refreshTokenHeader.replace("Bearer ", "");

    // Access Token 재발급
    String newAccessToken = refreshTokenService.reissue(refreshToken);
    log.info("토큰 재발급 완료");

    return ResponseEntity.ok(ApiResponse.success(new TokenResponse(newAccessToken, refreshToken)));
  }

  /** 로그아웃. Refresh Token을 무효화하여 재발급 차단. */
  @PostMapping("/logout")
  public void logout(@RequestHeader("Authorization") String refreshTokenHeader)
      throws BadRequestException {
    log.info("로그아웃 요청");

    String refreshToken = refreshTokenHeader.replace("Bearer ", "");
    authService.logout(refreshToken);
    log.info("로그아웃 완료");
  }
}
