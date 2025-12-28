package com.vatti.chzscout.backend.stream.application;

import com.vatti.chzscout.backend.stream.domain.AllFieldLiveDto;
import com.vatti.chzscout.backend.stream.domain.ChzzkLiveResponse;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 치지직 API에서 생방송 목록을 가져오는 서비스. */
@Service
@Slf4j
@RequiredArgsConstructor
public class StreamCacheService {

  private static final int MAX_PAGES = 10;

  private final ChzzkApiClient chzzkApiClient;

  /** 치지직 API에서 생방송 목록을 가져옵니다 (최대 10페이지). */
  public List<AllFieldLiveDto> fetchLiveStreams() {
    List<AllFieldLiveDto> allStreams = new ArrayList<>();
    String nextCursor = null;

    for (int page = 0; page < MAX_PAGES; page++) {
      ChzzkLiveResponse response = chzzkApiClient.getChzzkLive(nextCursor);
      log.info(
          "API Response - page {}: data={}, page={}",
          page + 1,
          response != null && response.data() != null ? response.data().size() : "null",
          response != null && response.page() != null ? response.page().next() : "null");

      if (response == null || response.data() == null || response.data().isEmpty()) {
        log.warn("Empty or null response at page {}, stopping pagination", page + 1);
        break;
      }

      allStreams.addAll(response.data());

      nextCursor = response.page() != null ? response.page().next() : null;
      if (nextCursor == null || nextCursor.isEmpty()) {
        log.info("No more pages, stopping at page {}", page + 1);
        break;
      }
    }

    log.info("Fetched {} live streams from API", allStreams.size());
    return allStreams;
  }
}
