package com.vatti.chzscout.backend.tag.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.vatti.chzscout.backend.common.response.ApiResponse;
import com.vatti.chzscout.backend.tag.application.usecase.TagUseCase;
import com.vatti.chzscout.backend.tag.domain.dto.TagAutocompleteResponse;
import com.vatti.chzscout.backend.tag.domain.entity.TagType;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TagControllerTest {

  @InjectMocks private TagController tagController;

  @Mock private TagUseCase tagUseCase;

  @Nested
  @DisplayName("autocomplete 메서드")
  class Autocomplete {

    @Test
    @DisplayName("자동완성 결과를 성공적으로 반환한다")
    void returnsAutocompleteResults() {
      // given
      String prefix = "롤";
      TagType tagType = TagType.CUSTOM;
      int limit = 10;

      List<TagAutocompleteResponse> expectedResults =
          List.of(
              TagAutocompleteResponse.of("롤", 200L),
              TagAutocompleteResponse.of("롤토체스", 50L),
              TagAutocompleteResponse.of("롤드컵", 150L));

      given(tagUseCase.searchAutocomplete(prefix, tagType, limit)).willReturn(expectedResults);

      // when
      ApiResponse<List<TagAutocompleteResponse>> response =
          tagController.autocomplete(prefix, tagType, limit);

      // then
      assertThat(response.isSuccess()).isTrue();
      assertThat(response.getData()).hasSize(3);
      assertThat(response.getData().get(0).name()).isEqualTo("롤");
      assertThat(response.getData().get(0).usageCount()).isEqualTo(200L);

      then(tagUseCase).should().searchAutocomplete(prefix, tagType, limit);
    }

    @Test
    @DisplayName("매칭 결과가 없으면 빈 리스트를 반환한다")
    void returnsEmptyListWhenNoMatch() {
      // given
      String prefix = "존재하지않는태그";
      TagType tagType = TagType.CUSTOM;
      int limit = 10;

      given(tagUseCase.searchAutocomplete(prefix, tagType, limit)).willReturn(List.of());

      // when
      ApiResponse<List<TagAutocompleteResponse>> response =
          tagController.autocomplete(prefix, tagType, limit);

      // then
      assertThat(response.isSuccess()).isTrue();
      assertThat(response.getData()).isEmpty();
    }

    @Test
    @DisplayName("CATEGORY 타입으로 자동완성을 요청한다")
    void searchesCategoryTags() {
      // given
      String prefix = "리그";
      TagType tagType = TagType.CATEGORY;
      int limit = 5;

      List<TagAutocompleteResponse> expectedResults =
          List.of(TagAutocompleteResponse.of("리그 오브 레전드", 500L));

      given(tagUseCase.searchAutocomplete(prefix, tagType, limit)).willReturn(expectedResults);

      // when
      ApiResponse<List<TagAutocompleteResponse>> response =
          tagController.autocomplete(prefix, tagType, limit);

      // then
      assertThat(response.isSuccess()).isTrue();
      assertThat(response.getData()).hasSize(1);
      assertThat(response.getData().get(0).name()).isEqualTo("리그 오브 레전드");

      then(tagUseCase).should().searchAutocomplete(prefix, tagType, limit);
    }
  }
}
