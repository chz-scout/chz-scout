package com.vatti.chzscout.backend.auth.application;

import com.vatti.chzscout.backend.auth.domain.dto.DiscordTokenResponse;
import com.vatti.chzscout.backend.auth.domain.dto.DiscordUserProfile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/** Discord OAuth API 클라이언트. */
@Component
public class DiscordOAuthClient {
  private final RestClient restClient;
  private final String clientId;
  private final String clientSecret;
  private final String redirectUri;
  private final String tokenUrl;
  private final String userInfoUrl;

  public DiscordOAuthClient(
      RestClient restClient,
      @Value("${discord.oauth.client-id}") String clientId,
      @Value("${discord.oauth.client-secret}") String clientSecret,
      @Value("${discord.oauth.redirect-uri}") String redirectUri,
      @Value("${discord.api.token-url}") String tokenUrl,
      @Value("${discord.api.user-info-url}") String userInfoUrl) {
    this.restClient = restClient;
    this.clientId = clientId;
    this.clientSecret = clientSecret;
    this.redirectUri = redirectUri;
    this.tokenUrl = tokenUrl;
    this.userInfoUrl = userInfoUrl;
  }

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
        .get()
        .uri(userInfoUrl)
        .header("Authorization", "Bearer " + accessToken)
        .retrieve()
        .body(DiscordUserProfile.class);
  }
}
