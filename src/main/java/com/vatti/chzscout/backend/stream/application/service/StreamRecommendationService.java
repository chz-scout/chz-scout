package com.vatti.chzscout.backend.stream.application.service;

import com.vatti.chzscout.backend.stream.application.usecase.RecommendStreamUseCase;
import com.vatti.chzscout.backend.stream.domain.EnrichedStreamDto;
import com.vatti.chzscout.backend.stream.domain.Stream;
import com.vatti.chzscout.backend.stream.infrastructure.redis.StreamRedisStore;
import java.util.*;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 방송 추천 서비스.
 *
 * <p>의미 태그를 기반으로 Redis에 캐싱된 방송과 매칭하여 추천합니다.
 *
 * <p>가중치 스코어링:
 *
 * <ul>
 *   <li>제목 매칭: 10점 (가장 확실한 매칭)
 *   <li>원본 태그 매칭: 5점 (스트리머/카테고리 설정)
 *   <li>AI 태그 매칭: 2점 (추론된 태그)
 * </ul>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class StreamRecommendationService implements RecommendStreamUseCase {

  private final StreamRedisStore streamRedisStore;

  private static final int MAX_RECOMMENDATIONS = 5;

  // 매칭 가중치
  private static final int TITLE_MATCH_WEIGHT = 10;
  private static final int ORIGINAL_TAG_WEIGHT = 5;
  private static final int AI_TAG_WEIGHT = 2;

  @Override
  public List<Stream> recommend(List<String> searchTags) {
    log.debug("방송 추천 요청 - tags: {}", searchTags);

    List<EnrichedStreamDto> liveStreams = streamRedisStore.findEnrichedStreams();

    List<ScoredStream> scoredStreams = new ArrayList<>();
    for (EnrichedStreamDto stream : liveStreams) {
      int score = calculateMatchScore(stream, searchTags);
      if (score > 0) {
        scoredStreams.add(new ScoredStream(stream, score));
      }
    }

    // 점수 내림차순 정렬
    scoredStreams.sort((a, b) -> b.score - a.score);

    int limit = Math.min(MAX_RECOMMENDATIONS, scoredStreams.size());
    List<Stream> results =
        scoredStreams.subList(0, limit).stream().map(sc -> Stream.from(sc.stream)).toList();

    log.debug(
        "추천 결과 - {}개 방송 (상위 점수: {})",
        results.size(),
        scoredStreams.isEmpty() ? 0 : scoredStreams.get(0).score);
    return results;
  }

  /**
   * 방송과 검색 태그 간의 매칭 점수를 계산합니다.
   *
   * <p>우선순위: 제목 > 원본 태그 > AI 태그
   */
  private int calculateMatchScore(EnrichedStreamDto stream, List<String> searchTags) {
    int score = 0;
    Set<String> originalTagSet = new HashSet<>(stream.originalTags());
    Set<String> enrichedTagSet = new HashSet<>(stream.enrichedTags());

    for (String tag : searchTags) {
      if (containsIgnoreCase(stream.liveTitle(), tag)) {
        // 1순위: 제목에 직접 포함
        score += TITLE_MATCH_WEIGHT;
      } else if (containsIgnoreCase(originalTagSet, tag)) {
        // 2순위: 원본 태그에 포함
        score += ORIGINAL_TAG_WEIGHT;
      } else if (containsIgnoreCase(enrichedTagSet, tag)) {
        // 3순위: AI 태그에만 포함
        score += AI_TAG_WEIGHT;
      }
    }

    return score;
  }

  /** 대소문자 무시 문자열 포함 검사 */
  private boolean containsIgnoreCase(String text, String search) {
    if (text == null || search == null) return false;
    return text.toLowerCase().contains(search.toLowerCase());
  }

  /** 대소문자 무시 Set 포함 검사 */
  private boolean containsIgnoreCase(Set<String> tags, String search) {
    if (tags == null || search == null) return false;
    String lowerSearch = search.toLowerCase();
    return tags.stream().anyMatch(tag -> tag.toLowerCase().contains(lowerSearch));
  }

  @AllArgsConstructor
  private static class ScoredStream {
    EnrichedStreamDto stream;
    int score;
  }
}
