package com.vatti.chzscout.backend.tag.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

import com.vatti.chzscout.backend.stream.domain.AllFieldLiveDto;
import com.vatti.chzscout.backend.stream.fixture.AllFieldLiveDtoFixture;
import com.vatti.chzscout.backend.tag.domain.dto.TagAutocompleteResponse;
import com.vatti.chzscout.backend.tag.domain.entity.Tag;
import com.vatti.chzscout.backend.tag.domain.entity.TagType;
import com.vatti.chzscout.backend.tag.fixture.TagFixture;
import com.vatti.chzscout.backend.tag.infrastructure.TagRepository;
import com.vatti.chzscout.backend.tag.infrastructure.redis.TagAutocompleteRedisStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TagServiceTest {
  @Mock TagRepository tagRepository;
  @Mock TagAutocompleteRedisStore tagAutocompleteRedisStore;

  @InjectMocks TagService tagService;

  @Nested
  @DisplayName("extractAndSaveTag")
  class ExtractAndSaveTag {
    @Test
    @DisplayName("기존 CUSTOM 태그는 usageCount 증가, 신규 CUSTOM 태그는 새로 생성한다")
    void extractAndSaveTagSuccess() {
      // given
      // 스트림 목록 생성: 태그 집계 → {서든: 1, 롤: 2, 메이플: 1, 게임: 1}, 카테고리 → {리그 오브 레전드: 2}
      List<AllFieldLiveDto> streams = new ArrayList<>();
      streams.add(AllFieldLiveDtoFixture.create(1, List.of("서든", "롤", "메이플")));
      streams.add(
          AllFieldLiveDtoFixture.create(2)); // 기본값: tags=["게임", "롤"], liveCategory="리그 오브 레전드"

      // CUSTOM 태그 Mock: "롤"만 DB에 존재
      Tag existingCustomTag = Tag.createCustom("롤", 0L);
      given(tagRepository.findCustomByNameInIncludingDeleted(any()))
          .willReturn(List.of(existingCustomTag));

      // CATEGORY 태그 Mock: "리그 오브 레전드"가 DB에 존재
      Tag existingCategoryTag = Tag.createCategory("리그 오브 레전드", 0L);
      given(tagRepository.findCategoryByNameInIncludingDeleted(any()))
          .willReturn(List.of(existingCategoryTag));

      // when
      tagService.extractAndSaveTag(streams);

      // then
      // CUSTOM 태그 처리 검증
      verify(tagRepository).findCustomByNameInIncludingDeleted(Set.of("서든", "롤", "메이플", "게임"));
      verify(tagRepository).restoreCustomByNames(Set.of("롤"));

      // CATEGORY 태그 처리 검증
      verify(tagRepository).findCategoryByNameInIncludingDeleted(Set.of("리그 오브 레전드"));
      verify(tagRepository).restoreCategoryByNames(Set.of("리그 오브 레전드"));

      // saveAll이 2번 호출됨 (CUSTOM용 + CATEGORY용)
      verify(tagRepository, times(2)).saveAll(any());

      // 기존 태그의 usageCount 증가 검증
      assertThat(existingCustomTag.getUsageCount()).isEqualTo(2L); // "롤"이 2번 등장
      assertThat(existingCategoryTag.getUsageCount()).isEqualTo(2L); // "리그 오브 레전드"가 2번 등장
    }

    @Test
    @DisplayName("모든 CUSTOM 태그가 신규면 전부 새로 생성한다")
    void allCustomTagsAreNew() {
      // given
      List<AllFieldLiveDto> streams = new ArrayList<>();
      streams.add(AllFieldLiveDtoFixture.create(1, List.of("서든", "롤", "메이플")));

      // CUSTOM: 모두 신규
      given(tagRepository.findCustomByNameInIncludingDeleted(any())).willReturn(List.of());

      // CATEGORY: "리그 오브 레전드" 신규
      given(tagRepository.findCategoryByNameInIncludingDeleted(any())).willReturn(List.of());

      // when
      tagService.extractAndSaveTag(streams);

      // then
      // CUSTOM 태그 처리 검증
      verify(tagRepository).findCustomByNameInIncludingDeleted(Set.of("서든", "롤", "메이플"));
      // 기존 태그 없으므로 restore 호출 안 됨 (early return)
      verify(tagRepository, never()).restoreCustomByNames(any());

      // CATEGORY 태그 처리 검증
      verify(tagRepository).findCategoryByNameInIncludingDeleted(Set.of("리그 오브 레전드"));
      // 기존 카테고리 없으므로 restore 호출 안 됨 (early return)
      verify(tagRepository, never()).restoreCategoryByNames(any());

      // saveAll로 신규 CUSTOM 태그가 저장됐는지 검증
      verify(tagRepository)
          .saveAll(
              argThat(
                  savedTags -> {
                    List<Tag> tagList = (List<Tag>) savedTags;
                    if (tagList.size() != 3) return false;
                    Set<String> savedNames =
                        tagList.stream()
                            .map(Tag::getName)
                            .collect(java.util.stream.Collectors.toSet());
                    boolean allCustomType =
                        tagList.stream().allMatch(tag -> tag.getTagType() == TagType.CUSTOM);
                    return savedNames.equals(Set.of("서든", "롤", "메이플")) && allCustomType;
                  }));

      // saveAll로 신규 CATEGORY 태그가 저장됐는지 검증
      verify(tagRepository)
          .saveAll(
              argThat(
                  savedTags -> {
                    List<Tag> tagList = (List<Tag>) savedTags;
                    if (tagList.size() != 1) return false;
                    Tag tag = tagList.get(0);
                    return tag.getName().equals("리그 오브 레전드")
                        && tag.getTagType() == TagType.CATEGORY;
                  }));
    }

    @Test
    @DisplayName("빈 방송 리스트를 받으면 아무 작업도 하지 않는다")
    void emptyStreams() {
      // given
      List<AllFieldLiveDto> streams = List.of();

      // when
      tagService.extractAndSaveTag(streams);

      // then - early return으로 인해 어떤 Repository 메서드도 호출되지 않음
      verify(tagRepository, never()).findCustomByNameInIncludingDeleted(any());
      verify(tagRepository, never()).findCategoryByNameInIncludingDeleted(any());
      verify(tagRepository, never()).restoreCustomByNames(any());
      verify(tagRepository, never()).restoreCategoryByNames(any());
      verify(tagRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("모든 CUSTOM 태그가 기존이면 usageCount만 증가하고 신규 저장은 없다")
    void allCustomTagsAreExisting() {
      // given
      List<AllFieldLiveDto> streams = new ArrayList<>();
      streams.add(AllFieldLiveDtoFixture.create(1, List.of("서든", "롤", "메이플")));

      // 모든 CUSTOM 태그가 DB에 이미 존재
      Tag tag1 = Tag.createCustom("서든", 0L);
      Tag tag2 = Tag.createCustom("롤", 0L);
      Tag tag3 = Tag.createCustom("메이플", 0L);
      given(tagRepository.findCustomByNameInIncludingDeleted(any()))
          .willReturn(List.of(tag1, tag2, tag3));

      // CATEGORY도 존재
      Tag categoryTag = Tag.createCategory("리그 오브 레전드", 0L);
      given(tagRepository.findCategoryByNameInIncludingDeleted(any()))
          .willReturn(List.of(categoryTag));

      // when
      tagService.extractAndSaveTag(streams);

      // then
      verify(tagRepository).findCustomByNameInIncludingDeleted(Set.of("서든", "롤", "메이플"));
      verify(tagRepository).restoreCustomByNames(Set.of("서든", "롤", "메이플"));

      verify(tagRepository).findCategoryByNameInIncludingDeleted(Set.of("리그 오브 레전드"));
      verify(tagRepository).restoreCategoryByNames(Set.of("리그 오브 레전드"));

      // saveAll이 2번 호출됨 (둘 다 빈 리스트)
      verify(tagRepository, times(2)).saveAll(any());

      // 모든 기존 태그의 usageCount가 1씩 증가
      assertThat(tag1.getUsageCount()).isEqualTo(1L);
      assertThat(tag2.getUsageCount()).isEqualTo(1L);
      assertThat(tag3.getUsageCount()).isEqualTo(1L);
      assertThat(categoryTag.getUsageCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("신규 태그 생성 시 올바른 TagType으로 생성된다")
    void createsTagsWithCorrectTagType() {
      // given
      List<AllFieldLiveDto> streams = new ArrayList<>();
      streams.add(AllFieldLiveDtoFixture.create(1, List.of("신규태그")));

      given(tagRepository.findCustomByNameInIncludingDeleted(any())).willReturn(List.of());
      given(tagRepository.findCategoryByNameInIncludingDeleted(any())).willReturn(List.of());

      // when
      tagService.extractAndSaveTag(streams);

      // then - CUSTOM 태그는 TagType.CUSTOM으로 생성
      verify(tagRepository)
          .saveAll(
              argThat(
                  savedTags -> {
                    List<Tag> tagList = (List<Tag>) savedTags;
                    if (tagList.isEmpty()) return true; // CATEGORY용 빈 리스트 호출 허용
                    return tagList.stream().allMatch(tag -> tag.getTagType() == TagType.CUSTOM);
                  }));
    }
  }

  @Nested
  @DisplayName("refreshAutoCompletedCache 메서드")
  class refreshAutocompleteCache {
    @Test
    @DisplayName("DB에서 조회한 태그를 Redis에 저장한다")
    void refreshAutocompleteCacheSuccess() {
      // given
      List<Tag> customTags =
          List.of(
              TagFixture.createCustom("롤"),
              TagFixture.createCustom("메이플"),
              TagFixture.createCustom("배그"));
      List<Tag> categoryTags =
          List.of(
              TagFixture.createCategory("리그 오브 레전드"),
              TagFixture.createCategory("메이플 스토리"),
              TagFixture.createCategory("Talk"));

      given(tagRepository.findAllCustomTags()).willReturn(customTags);
      given(tagRepository.findAllCategoryTags()).willReturn(categoryTags);

      // when
      tagService.refreshAutocompleteCache();

      // then
      verify(tagRepository).findAllCategoryTags();
      verify(tagRepository).findAllCustomTags();
      verify(tagAutocompleteRedisStore).saveAll(categoryTags, TagType.CATEGORY);
      verify(tagAutocompleteRedisStore).saveAll(customTags, TagType.CUSTOM);
    }

    @Test
    @DisplayName("CUSTOM 태그가 빈 리스트여도 Redis 저장을 시도한다")
    void refreshAutocompleteCacheWithEmptyCustomTags() {
      // given
      List<Tag> customTags = List.of();
      List<Tag> categoryTags = List.of(TagFixture.createCategory("리그 오브 레전드"));

      given(tagRepository.findAllCustomTags()).willReturn(customTags);
      given(tagRepository.findAllCategoryTags()).willReturn(categoryTags);

      // when
      tagService.refreshAutocompleteCache();

      // then
      verify(tagAutocompleteRedisStore).saveAll(customTags, TagType.CUSTOM);
      verify(tagAutocompleteRedisStore).saveAll(categoryTags, TagType.CATEGORY);
    }

    @Test
    @DisplayName("CATEGORY 태그가 빈 리스트여도 Redis 저장을 시도한다")
    void refreshAutocompleteCacheWithEmptyCategoryTags() {
      // given
      List<Tag> customTags = List.of(TagFixture.createCustom("롤"));
      List<Tag> categoryTags = List.of();

      given(tagRepository.findAllCustomTags()).willReturn(customTags);
      given(tagRepository.findAllCategoryTags()).willReturn(categoryTags);

      // when
      tagService.refreshAutocompleteCache();

      // then
      verify(tagAutocompleteRedisStore).saveAll(customTags, TagType.CUSTOM);
      verify(tagAutocompleteRedisStore).saveAll(categoryTags, TagType.CATEGORY);
    }

    @Test
    @DisplayName("모든 태그가 빈 리스트여도 Redis 저장을 시도한다")
    void refreshAutocompleteCacheWithAllEmptyTags() {
      // given
      List<Tag> customTags = List.of();
      List<Tag> categoryTags = List.of();

      given(tagRepository.findAllCustomTags()).willReturn(customTags);
      given(tagRepository.findAllCategoryTags()).willReturn(categoryTags);

      // when
      tagService.refreshAutocompleteCache();

      // then
      verify(tagAutocompleteRedisStore).saveAll(customTags, TagType.CUSTOM);
      verify(tagAutocompleteRedisStore).saveAll(categoryTags, TagType.CATEGORY);
    }
  }

  @Nested
  @DisplayName("searchAutocomplete 메서드")
  class searchAutocompleteCache {
    String prefix = "롤";
    int limit = 10;

    // CUSTOM 태그 - Redis에서 반환되는 형식: "tagName:usageCount"
    List<String> customRedisMembers =
        List.of(
            "롤:100",
            "롤토체스:10",
            "롤드컵:60",
            "롤 같이 할 사람:3",
            "롤리나잇:3",
            "롤 방송:32",
            "롤 페이커:200",
            "롤 미드:20",
            "롤 승급전:10",
            "롤 칼바람:60");

    // CATEGORY 태그 - "리"로 시작하는 카테고리만
    List<String> categoryRedisMembers = List.of("리그 오브 레전드:500", "리니지:100", "리듬게임:50");

    @Test
    @DisplayName("태그를 검색하면 usageCount 내림차순으로 리턴한다")
    void searchAutocompleteCacheSuccess() {
      // given
      given(tagAutocompleteRedisStore.findByPrefix(prefix, TagType.CUSTOM, limit))
          .willReturn(customRedisMembers);

      for (String member : customRedisMembers) {
        String tagName = member.substring(0, member.lastIndexOf(":"));
        Long usageCount = Long.parseLong(member.substring(member.lastIndexOf(":") + 1));
        given(tagAutocompleteRedisStore.extractTagName(member)).willReturn(tagName);
        given(tagAutocompleteRedisStore.extractUsageCount(member)).willReturn(usageCount);
      }

      // when
      List<TagAutocompleteResponse> tagAutocompleteResponses =
          tagService.searchAutocomplete(prefix, TagType.CUSTOM, limit);

      // then
      assertThat(tagAutocompleteResponses).hasSize(10);
      assertThat(tagAutocompleteResponses.get(0).name()).isEqualTo("롤 페이커");
      assertThat(tagAutocompleteResponses.get(0).usageCount()).isEqualTo(200L);
      assertThat(tagAutocompleteResponses.get(1).name()).isEqualTo("롤");
      assertThat(tagAutocompleteResponses.get(1).usageCount()).isEqualTo(100L);

      // CATEGORY 태그 검색
      String categoryPrefix = "리";
      given(tagAutocompleteRedisStore.findByPrefix(categoryPrefix, TagType.CATEGORY, limit))
          .willReturn(categoryRedisMembers);

      for (String member : categoryRedisMembers) {
        String tagName = member.substring(0, member.lastIndexOf(":"));
        Long usageCount = Long.parseLong(member.substring(member.lastIndexOf(":") + 1));
        given(tagAutocompleteRedisStore.extractTagName(member)).willReturn(tagName);
        given(tagAutocompleteRedisStore.extractUsageCount(member)).willReturn(usageCount);
      }

      // when
      List<TagAutocompleteResponse> categoryResponses =
          tagService.searchAutocomplete(categoryPrefix, TagType.CATEGORY, limit);

      // then
      assertThat(categoryResponses).hasSize(3);
      assertThat(categoryResponses.get(0).name()).isEqualTo("리그 오브 레전드");
      assertThat(categoryResponses.get(0).usageCount()).isEqualTo(500L);
    }

    @Test
    @DisplayName("결과가 limit보다 적으면 있는 만큼만 리턴한다")
    void searchAutocompleteCacheWithFewerResults() {
      // given
      List<String> fewMembers = List.of("롤:100", "롤드컵:60", "롤토체스:10");

      given(tagAutocompleteRedisStore.findByPrefix(prefix, TagType.CUSTOM, limit))
          .willReturn(fewMembers);

      for (String member : fewMembers) {
        String tagName = member.substring(0, member.lastIndexOf(":"));
        Long usageCount = Long.parseLong(member.substring(member.lastIndexOf(":") + 1));
        given(tagAutocompleteRedisStore.extractTagName(member)).willReturn(tagName);
        given(tagAutocompleteRedisStore.extractUsageCount(member)).willReturn(usageCount);
      }

      // when
      List<TagAutocompleteResponse> tagAutocompleteResponses =
          tagService.searchAutocomplete(prefix, TagType.CUSTOM, limit);

      // then
      assertThat(tagAutocompleteResponses).hasSize(3);
      // usageCount 내림차순 정렬 검증
      assertThat(tagAutocompleteResponses.get(0).name()).isEqualTo("롤");
      assertThat(tagAutocompleteResponses.get(0).usageCount()).isEqualTo(100L);
      assertThat(tagAutocompleteResponses.get(1).name()).isEqualTo("롤드컵");
      assertThat(tagAutocompleteResponses.get(2).name()).isEqualTo("롤토체스");
    }

    @Test
    @DisplayName("매칭되는 태그가 없으면 빈 리스트를 리턴한다")
    void searchAutocompleteCacheWithNoResults() {
      // given
      String unknownPrefix = "존재하지않는태그";
      given(tagAutocompleteRedisStore.findByPrefix(unknownPrefix, TagType.CUSTOM, limit))
          .willReturn(List.of());

      // when
      List<TagAutocompleteResponse> tagAutocompleteResponses =
          tagService.searchAutocomplete(unknownPrefix, TagType.CUSTOM, limit);

      // then
      assertThat(tagAutocompleteResponses).isEmpty();
    }
  }
}
