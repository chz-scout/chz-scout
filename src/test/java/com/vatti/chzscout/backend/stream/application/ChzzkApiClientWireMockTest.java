package com.vatti.chzscout.backend.stream.application;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.vatti.chzscout.backend.stream.domain.ChzzkLiveResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

@WireMockTest
class ChzzkApiClientWireMockTest {

  private ChzzkApiClient chzzkApiClient;

  private static final String CLIENT_ID = "test-client-id";
  private static final String CLIENT_SECRET = "test-client-secret";

  @BeforeEach
  void setUp(WireMockRuntimeInfo wireMockRuntimeInfo) {
    RestClient restClient = RestClient.builder().build();
    chzzkApiClient = new ChzzkApiClient(restClient);

    ReflectionTestUtils.setField(
        chzzkApiClient, "apiBaseUrl", wireMockRuntimeInfo.getHttpBaseUrl());
    ReflectionTestUtils.setField(chzzkApiClient, "apiClientId", CLIENT_ID);
    ReflectionTestUtils.setField(chzzkApiClient, "apiClientSecret", CLIENT_SECRET);
  }

  @Nested
  @DisplayName("getChzzkLive 메서드")
  class GetChzzkLive {

    @Test
    @DisplayName("성공적으로 생방송 목록을 조회한다")
    void success() {

      String mockResponse =
          """
          {
            "code": 200,
            "message": "OK",
            "content": {
              "data": [
                {
                  "liveId": 1,
                  "liveTitle": "테스트 방송",
                  "liveThumbnailImageUrl": "https://example.com/thumb.jpg",
                  "concurrentUserCount": 100,
                  "openDate": "2025-01-01T12:00:00",
                  "adult": false,
                  "tags": ["게임", "롤"],
                  "categoryType": "GAME",
                  "liveCategory": "League of Legends",
                  "liveCategoryValue": "lol",
                  "channelId": "ch123",
                  "channelName": "테스트 채널",
                  "channelImageUrl": "https://example.com/profile.jpg"
                }
              ],
              "page": {
                "next": "cursor123"
              }
            }
          }
          """;

      stubFor(
          get(urlPathEqualTo("/open/v1/lives"))
              .withHeader("Client-Id", equalTo(CLIENT_ID))
              .withHeader("Client-Secret", equalTo(CLIENT_SECRET))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                      .withBody(mockResponse)));

      // when
      ChzzkLiveResponse result = chzzkApiClient.getChzzkLive();

      // then
      assertThat(result).isNotNull();
      assertThat(result.code()).isEqualTo(200);
      assertThat(result.data()).hasSize(1);
      assertThat(result.data().get(0).liveTitle()).isEqualTo("테스트 방송");
      assertThat(result.page().next()).isEqualTo("cursor123");

      // 요청 검증
      verify(
          getRequestedFor(urlPathEqualTo("/open/v1/lives"))
              .withHeader("Client-Id", equalTo(CLIENT_ID))
              .withHeader("Client-Secret", equalTo(CLIENT_SECRET)));
    }

    @Test
    @DisplayName("커서를 전달하여 다음 페이지를 조회한다")
    void withCursor() {
      // given
      String cursor = "nextPageCursor";
      String mockResponse =
          """
          {
            "code": 200,
            "message": "OK",
            "content": {
              "data": [],
              "page": {
                "next": null
              }
            }
          }
          """;

      stubFor(
          get(urlPathEqualTo("/open/v1/lives"))
              .withQueryParam("next", equalTo(cursor))
              .withHeader("Client-Id", equalTo(CLIENT_ID))
              .withHeader("Client-Secret", equalTo(CLIENT_SECRET))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                      .withBody(mockResponse)));

      // when
      ChzzkLiveResponse result = chzzkApiClient.getChzzkLive(cursor);

      // then
      assertThat(result).isNotNull();
      assertThat(result.code()).isEqualTo(200);
      assertThat(result.data()).isEmpty();
      assertThat(result.page().next()).isNull();
    }
  }
}
