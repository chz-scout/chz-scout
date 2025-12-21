package com.vatti.chzscout.backend.tag.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;

import com.vatti.chzscout.backend.common.config.EmbeddedRedisConfig;
import com.vatti.chzscout.backend.tag.domain.entity.Tag;
import com.vatti.chzscout.backend.tag.domain.entity.TagType;
import com.vatti.chzscout.backend.tag.fixture.TagFixture;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(EmbeddedRedisConfig.class)
class TagAutocompleteRedisStoreTest {
  @Autowired TagAutocompleteRedisStore tagAutocompleteRedisStore;

  @Autowired private StringRedisTemplate stringRedisTemplate;

  private static final String CUSTOM_KEY = "tag:autocomplete:custom";
  private static final String CATEGORY_KEY = "tag:autocomplete:category";

  // CUSTOM 태그 테스트 데이터
  List<Tag> customTags =
      List.of(
          TagFixture.createCustom("롤", 200L),
          TagFixture.createCustom("롤토체스", 50L),
          TagFixture.createCustom("롤드컵", 150L),
          TagFixture.createCustom("메이플", 100L),
          TagFixture.createCustom("배그", 80L));

  // CATEGORY 태그 테스트 데이터
  List<Tag> categoryTags =
      List.of(
          TagFixture.createCategory("리그 오브 레전드", 500L),
          TagFixture.createCategory("리니지", 300L),
          TagFixture.createCategory("메이플스토리", 200L),
          TagFixture.createCategory("발로란트", 150L));

  @Nested
  @DisplayName("saveAll 메서드")
  class SaveAll {
    @BeforeEach
    void setup() {
      // 기존 데이터 삭제
      stringRedisTemplate.delete(CUSTOM_KEY);
      stringRedisTemplate.delete(CATEGORY_KEY);

      // 테스트 전 기존 데이터 삽입 (삭제 검증용)
      ZSetOperations<String, String> zSetOps = stringRedisTemplate.opsForZSet();
      zSetOps.add(CUSTOM_KEY, "기존태그1:999", 0);
      zSetOps.add(CUSTOM_KEY, "기존태그2:888", 0);
      zSetOps.add(CATEGORY_KEY, "기존카테고리:777", 0);
    }

    @Test
    @DisplayName("기존 데이터를 삭제하고 새 데이터로 교체한다")
    void saveAllReplacesExistingData() {
      // given - setup에서 기존 데이터 삽입됨

      // when
      tagAutocompleteRedisStore.saveAll(customTags, TagType.CUSTOM);

      // then - 기존 데이터가 삭제되고 새 데이터만 존재
      Long size = stringRedisTemplate.opsForZSet().zCard(CUSTOM_KEY);
      assertThat(size).isEqualTo(5L); // customTags 개수

      // 기존 데이터가 없어졌는지 확인
      Double oldDataScore = stringRedisTemplate.opsForZSet().score(CUSTOM_KEY, "기존태그1:999");
      assertThat(oldDataScore).isNull();
    }

    @Test
    @DisplayName("빈 리스트를 전달하면 기존 데이터가 유지된다")
    void saveAllWithEmptyListKeepsExistingData() {
      // given - setup에서 기존 데이터 삽입됨
      Long beforeSize = stringRedisTemplate.opsForZSet().zCard(CUSTOM_KEY);
      assertThat(beforeSize).isEqualTo(2L); // 기존태그1, 기존태그2

      // when
      tagAutocompleteRedisStore.saveAll(List.of(), TagType.CUSTOM);

      // then - 기존 데이터가 그대로 유지됨
      Long afterSize = stringRedisTemplate.opsForZSet().zCard(CUSTOM_KEY);
      assertThat(afterSize).isEqualTo(2L);

      // 기존 데이터가 여전히 존재하는지 확인
      Double oldDataScore = stringRedisTemplate.opsForZSet().score(CUSTOM_KEY, "기존태그1:999");
      assertThat(oldDataScore).isNotNull();
    }

    @Test
    @DisplayName("null을 전달하면 기존 데이터가 유지된다")
    void saveAllWithNullKeepsExistingData() {
      // given - setup에서 기존 데이터 삽입됨

      // when
      tagAutocompleteRedisStore.saveAll(null, TagType.CUSTOM);

      // then - 기존 데이터가 그대로 유지됨
      Long size = stringRedisTemplate.opsForZSet().zCard(CUSTOM_KEY);
      assertThat(size).isEqualTo(2L);
    }

    @Test
    @DisplayName("저장된 데이터는 tagName:usageCount 형식이다")
    void saveAllStoresCorrectFormat() {
      // given - setup에서 기존 데이터 삽입됨

      // when
      tagAutocompleteRedisStore.saveAll(customTags, TagType.CUSTOM);

      // then - 저장된 member 형식 확인
      Double score = stringRedisTemplate.opsForZSet().score(CUSTOM_KEY, "롤:200");
      assertThat(score).isNotNull();

      score = stringRedisTemplate.opsForZSet().score(CUSTOM_KEY, "메이플:100");
      assertThat(score).isNotNull();
    }
  }

  @Nested
  @DisplayName("findByPrefix 메서드")
  class FindByPrefix {
    @BeforeEach
    void setup() {
      // 기존 데이터 삭제
      stringRedisTemplate.delete(CUSTOM_KEY);
      stringRedisTemplate.delete(CATEGORY_KEY);

      // 테스트 데이터 저장
      tagAutocompleteRedisStore.saveAll(customTags, TagType.CUSTOM);
      tagAutocompleteRedisStore.saveAll(categoryTags, TagType.CATEGORY);
    }

    @Test
    @DisplayName("prefix로 시작하는 태그들을 반환한다")
    void findByPrefixReturnsMatchingTags() {
      // given
      String prefix = "롤";

      // when
      List<String> results = tagAutocompleteRedisStore.findByPrefix(prefix, TagType.CUSTOM, 10);

      // then
      assertThat(results).hasSize(3);
      assertThat(results).allMatch(result -> result.startsWith("롤"));
    }

    @Test
    @DisplayName("limit 개수만큼만 반환한다")
    void findByPrefixRespectsLimit() {
      // given
      String prefix = "롤";
      int limit = 2;

      // when
      List<String> results = tagAutocompleteRedisStore.findByPrefix(prefix, TagType.CUSTOM, limit);

      // then
      assertThat(results).hasSize(2);
    }

    @Test
    @DisplayName("매칭되는 태그가 없으면 빈 리스트를 반환한다")
    void findByPrefixReturnsEmptyListWhenNoMatch() {
      // given
      String prefix = "존재하지않는태그";

      // when
      List<String> results = tagAutocompleteRedisStore.findByPrefix(prefix, TagType.CUSTOM, 10);

      // then
      assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("null prefix를 전달하면 빈 리스트를 반환한다")
    void findByPrefixWithNullReturnsEmptyList() {
      // when
      List<String> results = tagAutocompleteRedisStore.findByPrefix(null, TagType.CUSTOM, 10);

      // then
      assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("빈 문자열 prefix를 전달하면 빈 리스트를 반환한다")
    void findByPrefixWithBlankReturnsEmptyList() {
      // when
      List<String> results = tagAutocompleteRedisStore.findByPrefix("   ", TagType.CUSTOM, 10);

      // then
      assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("TagType별로 분리되어 검색된다")
    void findByPrefixSearchesByTagType() {
      // given - "리"로 시작하는 태그: CUSTOM에는 없고, CATEGORY에는 "리그 오브 레전드", "리니지" 존재

      // when
      List<String> customResults = tagAutocompleteRedisStore.findByPrefix("리", TagType.CUSTOM, 10);
      List<String> categoryResults =
          tagAutocompleteRedisStore.findByPrefix("리", TagType.CATEGORY, 10);

      // then
      assertThat(customResults).isEmpty();
      assertThat(categoryResults).hasSize(2);
      assertThat(categoryResults).allMatch(result -> result.startsWith("리"));
    }

    @Test
    @DisplayName("결과는 name:usageCount 형식이다")
    void findByPrefixReturnsCorrectFormat() {
      // given
      String prefix = "메이플";

      // when
      List<String> results = tagAutocompleteRedisStore.findByPrefix(prefix, TagType.CUSTOM, 10);

      // then
      assertThat(results).hasSize(1);
      assertThat(results.get(0)).isEqualTo("메이플:100");
    }
  }
}
