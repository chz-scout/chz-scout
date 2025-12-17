package com.vatti.chzscout.backend.stream.application;

import com.vatti.chzscout.backend.stream.domain.AllFieldLiveDto;
import com.vatti.chzscout.backend.stream.domain.ChzzkLiveResponse;
import com.vatti.chzscout.backend.stream.infrastructure.redis.StreamRedisStore;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 생방송 데이터 캐싱을 조율하는 서비스. */
@Service
@Slf4j
@RequiredArgsConstructor
public class StreamCacheService {

  private static final int MAX_PAGES = 10;

  private final ChzzkApiClient chzzkApiClient;
  private final StreamRedisStore streamRedisStore;

  /** 치지직 API에서 생방송 목록을 가져와 Redis에 캐싱 (최대 10페이지). */
  public void refreshLiveStreams() {
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

    if (!allStreams.isEmpty()) {
      streamRedisStore.saveLiveStreams(allStreams);
      log.info("Cached {} live streams", allStreams.size());
    } else {
      log.warn("No streams to cache");
    }
  }

  /** 캐싱된 생방송 목록 조회. 캐시 미스 시 API 호출. */
  public List<AllFieldLiveDto> getLiveStreams() {
    List<AllFieldLiveDto> cached = streamRedisStore.findLiveStreams();

    if (cached != null) {
      return cached;
    }

    log.info("Cache miss, fetching from API");
    refreshLiveStreams();
    return streamRedisStore.findLiveStreams();
  }
}
