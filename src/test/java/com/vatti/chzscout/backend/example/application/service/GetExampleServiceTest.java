package com.vatti.chzscout.backend.example.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.vatti.chzscout.backend.common.exception.BusinessException;
import com.vatti.chzscout.backend.example.domain.dto.ExampleResponse;
import com.vatti.chzscout.backend.example.domain.entity.Example;
import com.vatti.chzscout.backend.example.exception.ExampleErrorCode;
import com.vatti.chzscout.backend.example.infrastructure.ExampleRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetExampleServiceTest {

  @InjectMocks private GetExampleService getExampleService;

  @Mock private ExampleRepository exampleRepository;

  @Test
  @DisplayName("Example 단건 조회 성공")
  void getExample_Success() {
    // given
    Long id = 1L;
    Example example = Example.create("테스트", "test@example.com");

    given(exampleRepository.findById(id)).willReturn(Optional.of(example));

    // when
    ExampleResponse response = getExampleService.execute(id);

    // then
    assertThat(response.name()).isEqualTo("테스트");
    assertThat(response.email()).isEqualTo("test@example.com");
  }

  @Test
  @DisplayName("Example 단건 조회 실패 - 존재하지 않음")
  void getExample_Fail_NotFound() {
    // given
    Long id = 1L;

    given(exampleRepository.findById(id)).willReturn(Optional.empty());

    // when & then
    assertThatThrownBy(() -> getExampleService.execute(id))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            e ->
                assertThat(((BusinessException) e).getErrorCode())
                    .isEqualTo(ExampleErrorCode.EXAMPLE_NOT_FOUND));
  }

  @Test
  @DisplayName("Example 전체 조회 성공")
  void getAllExamples_Success() {
    // given
    Example example1 = Example.create("테스트1", "test1@example.com");
    Example example2 = Example.create("테스트2", "test2@example.com");

    given(exampleRepository.findAll()).willReturn(List.of(example1, example2));

    // when
    List<ExampleResponse> responses = getExampleService.executeAll();

    // then
    assertThat(responses).hasSize(2);
    assertThat(responses.get(0).name()).isEqualTo("테스트1");
    assertThat(responses.get(1).name()).isEqualTo("테스트2");
  }
}
