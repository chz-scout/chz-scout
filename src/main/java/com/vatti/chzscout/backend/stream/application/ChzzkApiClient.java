package com.vatti.chzscout.backend.stream.application;

import com.vatti.chzscout.backend.stream.domain.ChzzkLiveResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@Slf4j
@RequiredArgsConstructor
public class ChzzkApiClient {

  @Value("${chzzk.api.base-url}")
  private String apiBaseUrl;

  @Value("${chzzk.api.client-id}")
  private String apiClientId;

  @Value("${chzzk.api.client-secret}")
  private String apiClientSecret;

  private final RestClient restClient;

  /** 현재 진행 중인 생방송 목록을 조회한다 (첫 페이지). */
  public ChzzkLiveResponse getChzzkLive() {
    return getChzzkLive(null);
  }

  /** 현재 진행 중인 생방송 목록을 조회한다 (커서 기반 페이지네이션). */
  public ChzzkLiveResponse getChzzkLive(String next) {
    String baseUri = apiBaseUrl + "/open/v1/lives";

    // RestClient URI 템플릿 사용 - 자동으로 URL 인코딩 처리
    var requestSpec =
        restClient
            .get()
            .uri(
                baseUri + (next != null && !next.isEmpty() ? "?next={next}" : ""),
                next != null ? next : "")
            .header("Client-Id", apiClientId)
            .header("Client-Secret", apiClientSecret)
            .accept(MediaType.APPLICATION_JSON);

    return requestSpec.retrieve().body(ChzzkLiveResponse.class);
  }
}
