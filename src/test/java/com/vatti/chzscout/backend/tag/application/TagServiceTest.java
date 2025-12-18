package com.vatti.chzscout.backend.tag.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

import com.vatti.chzscout.backend.stream.domain.AllFieldLiveDto;
import com.vatti.chzscout.backend.stream.fixture.AllFieldLiveDtoFixture;
import com.vatti.chzscout.backend.tag.domain.entity.Tag;
import com.vatti.chzscout.backend.tag.domain.entity.TagType;
import com.vatti.chzscout.backend.tag.infrastructure.TagRepository;
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
}
