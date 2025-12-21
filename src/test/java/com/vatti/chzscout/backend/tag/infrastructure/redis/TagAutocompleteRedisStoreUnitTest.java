package com.vatti.chzscout.backend.tag.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TagAutocompleteRedisStoreUnitTest {

  private TagAutocompleteRedisStore tagAutocompleteRedisStore;

  @BeforeEach
  void setup() {
    tagAutocompleteRedisStore = new TagAutocompleteRedisStore(null);
  }

  @Nested
  @DisplayName("extractTagName 메서드")
  class ExtractTagName {

    @Test
    @DisplayName("name:usageCount 형식에서 태그 이름을 추출한다")
    void extractsTagNameFromValidFormat() {
      // given
      String member = "롤:200";

      // when
      String tagName = tagAutocompleteRedisStore.extractTagName(member);

      // then
      assertThat(tagName).isEqualTo("롤");
    }

    @Test
    @DisplayName("콜론이 여러 개인 경우 마지막 콜론 기준으로 분리한다")
    void extractsTagNameWithMultipleColons() {
      // given
      String member = "리그:오브:레전드:500";

      // when
      String tagName = tagAutocompleteRedisStore.extractTagName(member);

      // then
      assertThat(tagName).isEqualTo("리그:오브:레전드");
    }

    @Test
    @DisplayName("콜론이 없으면 원본 문자열을 반환한다")
    void returnsOriginalWhenNoDelimiter() {
      // given
      String member = "태그이름만";

      // when
      String tagName = tagAutocompleteRedisStore.extractTagName(member);

      // then
      assertThat(tagName).isEqualTo("태그이름만");
    }
  }

  @Nested
  @DisplayName("extractUsageCount 메서드")
  class ExtractUsageCount {

    @Test
    @DisplayName("name:usageCount 형식에서 usageCount를 추출한다")
    void extractsUsageCountFromValidFormat() {
      // given
      String member = "롤:200";

      // when
      Long usageCount = tagAutocompleteRedisStore.extractUsageCount(member);

      // then
      assertThat(usageCount).isEqualTo(200L);
    }

    @Test
    @DisplayName("콜론이 여러 개인 경우 마지막 콜론 뒤의 값을 추출한다")
    void extractsUsageCountWithMultipleColons() {
      // given
      String member = "리그:오브:레전드:500";

      // when
      Long usageCount = tagAutocompleteRedisStore.extractUsageCount(member);

      // then
      assertThat(usageCount).isEqualTo(500L);
    }

    @Test
    @DisplayName("콜론이 없으면 0을 반환한다")
    void returnsZeroWhenNoDelimiter() {
      // given
      String member = "태그이름만";

      // when
      Long usageCount = tagAutocompleteRedisStore.extractUsageCount(member);

      // then
      assertThat(usageCount).isEqualTo(0L);
    }

    @Test
    @DisplayName("콜론으로 끝나면 0을 반환한다")
    void returnsZeroWhenEndsWithDelimiter() {
      // given
      String member = "태그:";

      // when
      Long usageCount = tagAutocompleteRedisStore.extractUsageCount(member);

      // then
      assertThat(usageCount).isEqualTo(0L);
    }

    @Test
    @DisplayName("usageCount가 숫자가 아니면 0을 반환한다")
    void returnsZeroWhenNotNumber() {
      // given
      String member = "태그:abc";

      // when
      Long usageCount = tagAutocompleteRedisStore.extractUsageCount(member);

      // then
      assertThat(usageCount).isEqualTo(0L);
    }
  }
}
