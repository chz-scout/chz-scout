package com.vatti.chzscout.backend.stream.infrastructure.redis;

import com.vatti.chzscout.backend.stream.domain.AllFieldLiveDto;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Repository
@RequiredArgsConstructor
public class StreamRedisStore {

  private static final String LIVE_STREAMS_KEY = "stream:lives";
  private static final Duration DEFAULT_TTL = Duration.ofMinutes(5);

  private final StringRedisTemplate stringRedisTemplate;
  private final JsonMapper jsonMapper;

  /** 생방송 목록을 Redis에 저장 (TTL 5분). */
  public void saveLiveStreams(List<AllFieldLiveDto> streams) {
    String json = jsonMapper.writeValueAsString(streams);
    stringRedisTemplate.opsForValue().set(LIVE_STREAMS_KEY, json, DEFAULT_TTL);
  }

  /** Redis에서 생방송 목록 조회. */
  public List<AllFieldLiveDto> findLiveStreams() {
    String json = stringRedisTemplate.opsForValue().get(LIVE_STREAMS_KEY);
    if (json == null) {
      return null;
    }
    return jsonMapper.readValue(json, new TypeReference<>() {});
  }
}
