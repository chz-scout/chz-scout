package com.vatti.chzscout.backend.auth.application;

import com.vatti.chzscout.backend.auth.domain.dto.DiscordTokenResponse;
import com.vatti.chzscout.backend.auth.domain.dto.DiscordUserProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/** Discord OAuth API 클라이언트. */
@Component
@RequiredArgsConstructor
public class DiscordOAuthClient {
  private final RestClient restClient = RestClient.create();

  @Value("${discord.oauth.client-id}")
  private String clientId;

  @Value("${discord.oauth.client-secret}")
  private String clientSecret;

  @Value("${discord.oauth.redirect-uri}")
  private String redirectUri;

  @Value("${discord.api.token-url}")
  private String tokenUrl;

  @Value("${discord.api.user-info-url}")
  private String userInfoUrl;

  public DiscordTokenResponse exchangeToken(String code) {
    MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
    body.add("code", code);
    body.add("client_id", clientId);
    body.add("client_secret", clientSecret);
    body.add("redirect_uri", redirectUri);
    body.add("grant_type", "authorization_code");

    return restClient
        .post()
        .uri(tokenUrl)
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .body(body)
        .retrieve()
        .body(DiscordTokenResponse.class);
  }

  public DiscordUserProfile getUserProfile(String accessToken) {
    return restClient
        .post()
        .uri(userInfoUrl)
        .header("Authorization", "Bearer " + accessToken)
        .retrieve()
        .body(DiscordUserProfile.class);
  }
}
