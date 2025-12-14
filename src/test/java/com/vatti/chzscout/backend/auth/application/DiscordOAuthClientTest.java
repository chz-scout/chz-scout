package com.vatti.chzscout.backend.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import com.vatti.chzscout.backend.auth.domain.dto.DiscordTokenResponse;
import com.vatti.chzscout.backend.auth.domain.dto.DiscordUserProfile;
import com.vatti.chzscout.backend.common.config.RestClientConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;

@RestClientTest(DiscordOAuthClient.class)
@Import(RestClientConfig.class)
@TestPropertySource(
    properties = {
      "discord.oauth.client-id=test-client-id",
      "discord.oauth.client-secret=test-client-secret",
      "discord.oauth.redirect-uri=http://localhost:3000/callback",
      "discord.api.token-url=https://discord.com/api/oauth2/token",
      "discord.api.user-info-url=https://discord.com/api/users/@me"
    })
class DiscordOAuthClientTest {

  @Autowired private DiscordOAuthClient discordOAuthClient;

  @Autowired private MockRestServiceServer mockServer;

  @Nested
  @DisplayName("exchangeToken 메서드")
  class ExchangeTokenTest {

    @Test
    @DisplayName("인가 코드로 Discord 토큰 교환 성공")
    void exchangeToken_Success() {
      // given
      String code = "test-authorization-code";
      String responseJson =
          """
          {
            "access_token": "discord-access-token",
            "token_type": "Bearer",
            "expires_in": 604800,
            "refresh_token": "discord-refresh-token",
            "scope": "identify email"
          }
          """;

      mockServer
          .expect(requestTo("https://discord.com/api/oauth2/token"))
          .andExpect(method(HttpMethod.POST))
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
          .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

      // when
      DiscordTokenResponse result = discordOAuthClient.exchangeToken(code);

      // then
      assertThat(result.accessToken()).isEqualTo("discord-access-token");
      assertThat(result.tokenType()).isEqualTo("Bearer");
      assertThat(result.expiresIn()).isEqualTo(604800);
      assertThat(result.refreshToken()).isEqualTo("discord-refresh-token");
      assertThat(result.scope()).isEqualTo("identify email");

      mockServer.verify();
    }
  }

  @Nested
  @DisplayName("getUserProfile 메서드")
  class GetUserProfileTest {

    @Test
    @DisplayName("액세스 토큰으로 사용자 프로필 조회 성공")
    void getUserProfile_Success() {
      // given
      String accessToken = "discord-access-token";
      String responseJson =
          """
          {
            "id": "123456789",
            "username": "testuser",
            "global_name": "Test User",
            "avatar": "avatar-hash",
            "email": "test@example.com",
            "verified": true
          }
          """;

      mockServer
          .expect(requestTo("https://discord.com/api/users/@me"))
          .andExpect(method(HttpMethod.GET))
          .andExpect(header("Authorization", "Bearer " + accessToken))
          .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

      // when
      DiscordUserProfile result = discordOAuthClient.getUserProfile(accessToken);

      // then
      assertThat(result.id()).isEqualTo("123456789");
      assertThat(result.username()).isEqualTo("testuser");
      assertThat(result.globalName()).isEqualTo("Test User");
      assertThat(result.avatar()).isEqualTo("avatar-hash");
      assertThat(result.email()).isEqualTo("test@example.com");
      assertThat(result.verified()).isTrue();

      mockServer.verify();
    }

    @Test
    @DisplayName("이메일이 null인 경우에도 프로필 조회 성공")
    void getUserProfile_WithNullEmail_Success() {
      // given
      String accessToken = "discord-access-token";
      String responseJson =
          """
          {
            "id": "123456789",
            "username": "testuser",
            "global_name": null,
            "avatar": null,
            "email": null,
            "verified": false
          }
          """;

      mockServer
          .expect(requestTo("https://discord.com/api/users/@me"))
          .andExpect(method(HttpMethod.GET))
          .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

      // when
      DiscordUserProfile result = discordOAuthClient.getUserProfile(accessToken);

      // then
      assertThat(result.id()).isEqualTo("123456789");
      assertThat(result.username()).isEqualTo("testuser");
      assertThat(result.email()).isNull();

      mockServer.verify();
    }
  }
}
