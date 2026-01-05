package com.vatti.chzscout.backend.stream.infrastructure.redis;

import com.vatti.chzscout.backend.stream.domain.AllFieldLiveDto;
import com.vatti.chzscout.backend.stream.domain.EnrichedStreamDto;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.DigestUtils;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * 방송 정보 Redis 저장소.
 *
 * <p>Enriched 방송 목록 캐싱 및 변경 감지 기능을 제공합니다.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class StreamRedisStore {

  private static final String ENRICHED_STREAMS_KEY = "stream:enriched";
  private static final String STREAM_HASHES_KEY = "stream:hashes";
  private static final Duration DEFAULT_TTL = Duration.ofMinutes(15);

  private final StringRedisTemplate redisTemplate;
  private final JsonMapper jsonMapper;

  /**
   * AI 태그가 추가된 방송 목록을 Redis에 저장합니다.
   *
   * @param streams EnrichedStreamDto 목록
   */
  public void saveEnrichedStreams(List<EnrichedStreamDto> streams) {
    String json = jsonMapper.writeValueAsString(streams);
    redisTemplate.opsForValue().set(ENRICHED_STREAMS_KEY, json, DEFAULT_TTL);
    log.debug("Enriched 방송 {}개 저장", streams.size());
  }

  /**
   * Redis에서 Enriched 방송 목록을 조회합니다.
   *
   * @return EnrichedStreamDto 목록 (없으면 빈 리스트)
   */
  public List<EnrichedStreamDto> findEnrichedStreams() {
    String json = redisTemplate.opsForValue().get(ENRICHED_STREAMS_KEY);
    if (json == null) {
      return List.of();
    }
    return jsonMapper.readValue(json, new TypeReference<>() {});
  }

  /**
   * 신규 또는 변경된 방송을 감지합니다.
   *
   * <p>originalTags를 해시화하여 이전 해시와 비교합니다.
   *
   * @param currentStreams 현재 방송 목록
   * @return 변경 감지 결과 (신규, 변경됨, 종료됨)
   */
  public StreamChangeResult detectChanges(List<AllFieldLiveDto> currentStreams) {
    // 현재 방송들의 해시 계산 (originalTags 기준)
    // 중복 channelId가 있을 경우 마지막 값을 사용 (업스트림 API 중복 응답 대비)
    Map<String, String> currentHashes =
        currentStreams.stream()
            .collect(
                Collectors.toMap(
                    AllFieldLiveDto::channelId,
                    this::computeOriginalTagsHash,
                    (existing, replacement) -> replacement));

    // 이전 해시 조회
    Map<Object, Object> previousHashEntries = redisTemplate.opsForHash().entries(STREAM_HASHES_KEY);
    Map<String, String> previousHashes =
        previousHashEntries.entrySet().stream()
            .collect(Collectors.toMap(e -> (String) e.getKey(), e -> (String) e.getValue()));

    // 변경 감지
    Set<String> newStreams = new HashSet<>();
    Set<String> changedStreams = new HashSet<>();

    for (var entry : currentHashes.entrySet()) {
      String channelId = entry.getKey();
      String currentHash = entry.getValue();
      String previousHash = previousHashes.get(channelId);

      if (previousHash == null) { // 아예 신규 방송
        newStreams.add(channelId);
      } else if (!currentHash.equals(previousHash)) { // 변경된 방송
        changedStreams.add(channelId);
      }
    }

    // 종료된 방송
    Set<String> endedStreams = new HashSet<>(previousHashes.keySet());
    endedStreams.removeAll(currentHashes.keySet());

    // 현재 해시 저장 (다음 비교를 위해)
    saveCurrentHashes(currentHashes);

    log.info(
        "변경 감지 완료 - 신규: {}개, 변경: {}개, 종료: {}개",
        newStreams.size(),
        changedStreams.size(),
        endedStreams.size());

    return new StreamChangeResult(newStreams, changedStreams, endedStreams);
  }

  private void saveCurrentHashes(Map<String, String> currentHashes) {
    redisTemplate.delete(STREAM_HASHES_KEY);
    if (!currentHashes.isEmpty()) {
      redisTemplate.opsForHash().putAll(STREAM_HASHES_KEY, currentHashes);
      redisTemplate.expire(STREAM_HASHES_KEY, DEFAULT_TTL);
    }
  }

  /**
   * originalTags 기준으로 해시를 계산합니다.
   *
   * <p>카테고리 + 기존 태그만 사용하여 AI 태그 변경과 무관하게 비교합니다.
   */
  private String computeOriginalTagsHash(AllFieldLiveDto stream) {
    StringBuilder sb = new StringBuilder();

    // 카테고리
    if (stream.liveCategoryValue() != null) {
      sb.append(stream.liveCategoryValue());
    }
    sb.append("|");

    // 기존 태그
    if (stream.tags() != null) {
      sb.append(String.join(",", stream.tags()));
    }
    sb.append("|");

    // 제목도 포함 (제목 변경 시 태그 재추출 필요)
    if (stream.liveTitle() != null) {
      sb.append(stream.liveTitle());
    }

    return DigestUtils.md5DigestAsHex(sb.toString().getBytes());
  }

  /** 변경 감지 결과. */
  public record StreamChangeResult(
      Set<String> newStreams, Set<String> changedStreams, Set<String> endedStreams) {

    /** 신규 또는 변경된 방송이 있는지 확인합니다. */
    public boolean hasChanges() {
      return !newStreams.isEmpty() || !changedStreams.isEmpty();
    }

    /** 신규 + 변경된 모든 channelId를 반환합니다. */
    public Set<String> getAllChangedIds() {
      Set<String> all = new HashSet<>(newStreams);
      all.addAll(changedStreams);
      return all;
    }
  }
}
