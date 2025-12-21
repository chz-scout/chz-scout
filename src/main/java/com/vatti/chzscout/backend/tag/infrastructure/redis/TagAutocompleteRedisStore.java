package com.vatti.chzscout.backend.tag.infrastructure.redis;

import com.vatti.chzscout.backend.tag.domain.entity.Tag;
import com.vatti.chzscout.backend.tag.domain.entity.TagType;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.Limit;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class TagAutocompleteRedisStore {

  private static final String KEY_PREFIX = "tag:autocomplete:";
  private static final String MEMBER_DELIMITER = ":";

  private final StringRedisTemplate stringRedisTemplate;

  /**
   * 태그 목록을 Redis Sorted Set에 저장합니다.
   *
   * <p>기존 데이터를 모두 삭제하고 새로운 데이터로 교체합니다. Member 형식: {tagName}:{usageCount}, Score: 0 (ZRANGEBYLEX 사용을
   * 위해 동일 score)
   *
   * @param tags 저장할 태그 목록
   * @param tagType 태그 타입 (CATEGORY 또는 CUSTOM)
   */
  public void saveAll(List<Tag> tags, TagType tagType) {
    if (tags == null || tags.isEmpty()) {
      return;
    }

    String key = generateKey(tagType);
    ZSetOperations<String, String> zSetOps = stringRedisTemplate.opsForZSet();

    // 기존 데이터 삭제 후 새로 저장 (전체 갱신)
    stringRedisTemplate.delete(key);

    Set<TypedTuple<String>> tuples =
        tags.stream()
            .map(tag -> TypedTuple.of(formatMember(tag.getName(), tag.getUsageCount()), 0.0))
            .collect(Collectors.toSet());

    zSetOps.add(key, tuples);
  }

  /**
   * prefix로 시작하는 태그를 검색합니다.
   *
   * <p>ZRANGEBYLEX를 사용하여 사전순 범위 검색을 수행합니다.
   *
   * @param prefix 검색할 접두어
   * @param tagType 태그 타입
   * @param limit 최대 결과 수
   * @return 매칭되는 태그 목록 (name:usageCount 형식)
   */
  public List<String> findByPrefix(String prefix, TagType tagType, int limit) {
    if (prefix == null || prefix.isBlank()) {
      return Collections.emptyList();
    }

    String key = generateKey(tagType);
    ZSetOperations<String, String> zSetOps = stringRedisTemplate.opsForZSet();

    // ZRANGEBYLEX key [prefix [prefix\xff LIMIT 0 limit
    // \xff는 UTF-8에서 가장 큰 바이트로, prefix로 시작하는 모든 문자열을 포함
    Range<String> range = Range.closed(prefix, prefix + "\uffff");

    Set<String> results = zSetOps.rangeByLex(key, range, Limit.limit().count(limit));

    return results == null ? Collections.emptyList() : List.copyOf(results);
  }

  /**
   * Member 문자열에서 태그 이름을 추출합니다.
   *
   * @param member {tagName}:{usageCount} 형식의 문자열
   * @return 태그 이름
   */
  public String extractTagName(String member) {
    int lastDelimiterIndex = member.lastIndexOf(MEMBER_DELIMITER);
    if (lastDelimiterIndex == -1) {
      return member;
    }
    return member.substring(0, lastDelimiterIndex);
  }

  /**
   * Member 문자열에서 usageCount를 추출합니다.
   *
   * @param member {tagName}:{usageCount} 형식의 문자열
   * @return usageCount
   */
  public Long extractUsageCount(String member) {
    int lastDelimiterIndex = member.lastIndexOf(MEMBER_DELIMITER);
    if (lastDelimiterIndex == -1 || lastDelimiterIndex == member.length() - 1) {
      return 0L;
    }
    try {
      return Long.parseLong(member.substring(lastDelimiterIndex + 1));
    } catch (NumberFormatException e) {
      return 0L;
    }
  }

  private String generateKey(TagType tagType) {
    return KEY_PREFIX + tagType.name().toLowerCase();
  }

  private String formatMember(String tagName, Long usageCount) {
    return tagName + MEMBER_DELIMITER + usageCount;
  }
}
