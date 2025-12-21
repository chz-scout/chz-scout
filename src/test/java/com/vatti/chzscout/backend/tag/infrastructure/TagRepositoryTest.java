package com.vatti.chzscout.backend.tag.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.vatti.chzscout.backend.common.config.DataJpaTestConfig;
import com.vatti.chzscout.backend.tag.domain.entity.Tag;
import com.vatti.chzscout.backend.tag.fixture.TagFixture;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(DataJpaTestConfig.class)
class TagRepositoryTest {
  @Autowired TagRepository tagRepository;

  List<Tag> activeCustomTags;
  List<Tag> activeCategoryTags;
  List<Tag> deletedCustomTags;
  List<Tag> deletedCategoryTags;

  @BeforeEach
  void setup() {
    // 리스트 초기화
    activeCustomTags = new ArrayList<>();
    activeCategoryTags = new ArrayList<>();
    deletedCustomTags = new ArrayList<>();
    deletedCategoryTags = new ArrayList<>();

    // 활성 태그 생성 (각 5개)
    for (int i = 0; i < 5; i++) {
      activeCustomTags.add(TagFixture.createCustom("custom_active_" + i, (long) i));
      activeCategoryTags.add(TagFixture.createCategory("category_active_" + i, (long) i));
    }

    // 삭제될 태그 생성 (각 3개)
    for (int i = 0; i < 3; i++) {
      deletedCustomTags.add(TagFixture.createCustom("custom_deleted_" + i, (long) i));
      deletedCategoryTags.add(TagFixture.createCategory("category_deleted_" + i, (long) i));
    }

    // 저장
    tagRepository.saveAll(activeCustomTags);
    tagRepository.saveAll(activeCategoryTags);
    tagRepository.saveAll(deletedCustomTags);
    tagRepository.saveAll(deletedCategoryTags);

    // 삭제 처리
    tagRepository.deleteAll(deletedCustomTags);
    tagRepository.deleteAll(deletedCategoryTags);
  }

  @Nested
  @DisplayName("findCustomByNameInIncludingDeleted 메서드 테스트")
  class FindCustomByNameInIncludingDeleted {

    @Test
    @DisplayName("삭제된 CUSTOM 태그도 포함해서 조회한다")
    void includesDeletedCustomTags() {
      // given
      List<String> allCustomNames = new ArrayList<>();
      activeCustomTags.forEach(tag -> allCustomNames.add(tag.getName()));
      deletedCustomTags.forEach(tag -> allCustomNames.add(tag.getName()));

      // when
      List<Tag> result =
          tagRepository.findCustomByNameInIncludingDeleted(new HashSet<>(allCustomNames));

      // then - 활성 5개 + 삭제 3개 = 8개
      assertThat(result).hasSize(8);
    }

    @Test
    @DisplayName("CATEGORY 태그는 조회하지 않는다")
    void excludesCategoryTags() {
      // given - CUSTOM과 CATEGORY 이름을 모두 포함
      List<String> mixedNames = new ArrayList<>();
      activeCustomTags.forEach(tag -> mixedNames.add(tag.getName()));
      activeCategoryTags.forEach(tag -> mixedNames.add(tag.getName()));

      // when
      List<Tag> result =
          tagRepository.findCustomByNameInIncludingDeleted(new HashSet<>(mixedNames));

      // then - CUSTOM만 5개 반환
      assertThat(result).hasSize(5);
      assertThat(result).allMatch(tag -> tag.getName().startsWith("custom_"));
    }
  }

  @Nested
  @DisplayName("findCategoryByNameInIncludingDeleted 메서드 테스트")
  class FindCategoryByNameInIncludingDeleted {

    @Test
    @DisplayName("삭제된 CATEGORY 태그도 포함해서 조회한다")
    void includesDeletedCategoryTags() {
      // given
      List<String> allCategoryNames = new ArrayList<>();
      activeCategoryTags.forEach(tag -> allCategoryNames.add(tag.getName()));
      deletedCategoryTags.forEach(tag -> allCategoryNames.add(tag.getName()));

      // when
      List<Tag> result =
          tagRepository.findCategoryByNameInIncludingDeleted(new HashSet<>(allCategoryNames));

      // then - 활성 5개 + 삭제 3개 = 8개
      assertThat(result).hasSize(8);
    }

    @Test
    @DisplayName("CUSTOM 태그는 조회하지 않는다")
    void excludesCustomTags() {
      // given - CUSTOM과 CATEGORY 이름을 모두 포함
      List<String> mixedNames = new ArrayList<>();
      activeCustomTags.forEach(tag -> mixedNames.add(tag.getName()));
      activeCategoryTags.forEach(tag -> mixedNames.add(tag.getName()));

      // when
      List<Tag> result =
          tagRepository.findCategoryByNameInIncludingDeleted(new HashSet<>(mixedNames));

      // then - CATEGORY만 5개 반환
      assertThat(result).hasSize(5);
      assertThat(result).allMatch(tag -> tag.getName().startsWith("category_"));
    }
  }

  @Nested
  @DisplayName("restoreCustomByNames 메서드 테스트")
  class RestoreCustomByNames {

    @Test
    @DisplayName("삭제된 CUSTOM 태그를 복구한다")
    void restoresDeletedCustomTags() {
      // given
      List<String> deletedNames = deletedCustomTags.stream().map(Tag::getName).toList();

      // when
      int restoredCount = tagRepository.restoreCustomByNames(new HashSet<>(deletedNames));

      // then
      assertThat(restoredCount).isEqualTo(3);

      // 복구 후 일반 조회로 확인
      List<Tag> found = tagRepository.findByNameIn(new HashSet<>(deletedNames));
      assertThat(found).hasSize(3);
    }

    @Test
    @DisplayName("이미 활성 상태인 CUSTOM 태그는 영향받지 않는다")
    void doesNotAffectActiveTags() {
      // given
      List<String> activeNames = activeCustomTags.stream().map(Tag::getName).toList();

      // when
      int restoredCount = tagRepository.restoreCustomByNames(new HashSet<>(activeNames));

      // then - 이미 활성이므로 복구 대상 없음
      assertThat(restoredCount).isEqualTo(0);
    }

    @Test
    @DisplayName("CATEGORY 태그는 복구하지 않는다")
    void doesNotRestoreCategoryTags() {
      // given
      List<String> deletedCategoryNames = deletedCategoryTags.stream().map(Tag::getName).toList();

      // when
      int restoredCount = tagRepository.restoreCustomByNames(new HashSet<>(deletedCategoryNames));

      // then - CATEGORY는 복구 대상 아님
      assertThat(restoredCount).isEqualTo(0);
    }
  }

  @Nested
  @DisplayName("restoreCategoryByNames 메서드 테스트")
  class RestoreCategoryByNames {

    @Test
    @DisplayName("삭제된 CATEGORY 태그를 복구한다")
    void restoresDeletedCategoryTags() {
      // given
      List<String> deletedNames = deletedCategoryTags.stream().map(Tag::getName).toList();

      // when
      int restoredCount = tagRepository.restoreCategoryByNames(new HashSet<>(deletedNames));

      // then
      assertThat(restoredCount).isEqualTo(3);

      // 복구 후 일반 조회로 확인
      List<Tag> found = tagRepository.findByNameIn(new HashSet<>(deletedNames));
      assertThat(found).hasSize(3);
    }

    @Test
    @DisplayName("이미 활성 상태인 CATEGORY 태그는 영향받지 않는다")
    void doesNotAffectActiveTags() {
      // given
      List<String> activeNames = activeCategoryTags.stream().map(Tag::getName).toList();

      // when
      int restoredCount = tagRepository.restoreCategoryByNames(new HashSet<>(activeNames));

      // then - 이미 활성이므로 복구 대상 없음
      assertThat(restoredCount).isEqualTo(0);
    }

    @Test
    @DisplayName("CUSTOM 태그는 복구하지 않는다")
    void doesNotRestoreCustomTags() {
      // given
      List<String> deletedCustomNames = deletedCustomTags.stream().map(Tag::getName).toList();

      // when
      int restoredCount = tagRepository.restoreCategoryByNames(new HashSet<>(deletedCustomNames));

      // then - CUSTOM은 복구 대상 아님
      assertThat(restoredCount).isEqualTo(0);
    }
  }

  @Nested
  @DisplayName("findAllCustomTags 메서드 테스트")
  class FindAllCustomTags {

    @Test
    @DisplayName("활성 CUSTOM 태그만 조회한다")
    void findsOnlyActiveCustomTags() {
      // when
      List<Tag> result = tagRepository.findAllCustomTags();

      // then - 활성 CUSTOM 5개만 조회
      assertThat(result).hasSize(5);
      assertThat(result).allMatch(tag -> tag.getName().startsWith("custom_active_"));
    }

    @Test
    @DisplayName("CATEGORY 태그는 조회하지 않는다")
    void excludesCategoryTags() {
      // when
      List<Tag> result = tagRepository.findAllCustomTags();

      // then - CATEGORY 태그 없음
      assertThat(result).noneMatch(tag -> tag.getName().startsWith("category_"));
    }

    @Test
    @DisplayName("삭제된 CUSTOM 태그는 조회하지 않는다")
    void excludesDeletedCustomTags() {
      // when
      List<Tag> result = tagRepository.findAllCustomTags();

      // then - 삭제된 태그 없음
      assertThat(result).noneMatch(tag -> tag.getName().contains("deleted"));
    }
  }

  @Nested
  @DisplayName("findAllCategoryTags 메서드 테스트")
  class FindAllCategoryTags {

    @Test
    @DisplayName("활성 CATEGORY 태그만 조회한다")
    void findsOnlyActiveCategoryTags() {
      // when
      List<Tag> result = tagRepository.findAllCategoryTags();

      // then - 활성 CATEGORY 5개만 조회
      assertThat(result).hasSize(5);
      assertThat(result).allMatch(tag -> tag.getName().startsWith("category_active_"));
    }

    @Test
    @DisplayName("CUSTOM 태그는 조회하지 않는다")
    void excludesCustomTags() {
      // when
      List<Tag> result = tagRepository.findAllCategoryTags();

      // then - CUSTOM 태그 없음
      assertThat(result).noneMatch(tag -> tag.getName().startsWith("custom_"));
    }

    @Test
    @DisplayName("삭제된 CATEGORY 태그는 조회하지 않는다")
    void excludesDeletedCategoryTags() {
      // when
      List<Tag> result = tagRepository.findAllCategoryTags();

      // then - 삭제된 태그 없음
      assertThat(result).noneMatch(tag -> tag.getName().contains("deleted"));
    }
  }

  @Nested
  @DisplayName("findByNameIn 메서드 테스트")
  class FindByNameIn {

    @Test
    @DisplayName("활성 태그만 조회한다")
    void findsOnlyActiveTags() {
      // given
      List<String> allNames = new ArrayList<>();
      activeCustomTags.forEach(tag -> allNames.add(tag.getName()));
      deletedCustomTags.forEach(tag -> allNames.add(tag.getName()));

      // when
      List<Tag> result = tagRepository.findByNameIn(new HashSet<>(allNames));

      // then - 활성 태그만 5개
      assertThat(result).hasSize(5);
      assertThat(result).allMatch(tag -> tag.getName().startsWith("custom_active_"));
    }

    @Test
    @DisplayName("CUSTOM과 CATEGORY 모두 조회한다")
    void findsBothTypes() {
      // given
      List<String> activeNames = new ArrayList<>();
      activeCustomTags.forEach(tag -> activeNames.add(tag.getName()));
      activeCategoryTags.forEach(tag -> activeNames.add(tag.getName()));

      // when
      List<Tag> result = tagRepository.findByNameIn(new HashSet<>(activeNames));

      // then - CUSTOM 5개 + CATEGORY 5개 = 10개
      assertThat(result).hasSize(10);
    }
  }
}
