package com.vatti.chzscout.backend.example.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.vatti.chzscout.backend.common.exception.BusinessException;
import com.vatti.chzscout.backend.example.domain.dto.CreateExampleRequest;
import com.vatti.chzscout.backend.example.domain.dto.ExampleResponse;
import com.vatti.chzscout.backend.example.domain.entity.Example;
import com.vatti.chzscout.backend.example.exception.ExampleErrorCode;
import com.vatti.chzscout.backend.example.infrastructure.ExampleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CreateExampleServiceTest {

  @InjectMocks private CreateExampleService createExampleService;

  @Mock private ExampleRepository exampleRepository;

  @Test
  @DisplayName("Example 생성 성공")
  void createExample_Success() {
    // given
    CreateExampleRequest request = new CreateExampleRequest("테스트", "test@example.com");
    Example example = Example.create(request.name(), request.email());

    given(exampleRepository.existsByEmail(request.email())).willReturn(false);
    given(exampleRepository.save(any(Example.class))).willReturn(example);

    // when
    ExampleResponse response = createExampleService.execute(request);

    // then
    assertThat(response.name()).isEqualTo("테스트");
    assertThat(response.email()).isEqualTo("test@example.com");
  }

  @Test
  @DisplayName("Example 생성 실패 - 이미 존재하는 이메일")
  void createExample_Fail_AlreadyExists() {
    // given
    CreateExampleRequest request = new CreateExampleRequest("테스트", "test@example.com");

    given(exampleRepository.existsByEmail(request.email())).willReturn(true);

    // when & then
    assertThatThrownBy(() -> createExampleService.execute(request))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            e ->
                assertThat(((BusinessException) e).getErrorCode())
                    .isEqualTo(ExampleErrorCode.EXAMPLE_ALREADY_EXISTS));
  }
}
