package com.vatti.chzscout.backend.example.application.service;

import com.vatti.chzscout.backend.common.exception.BusinessException;
import com.vatti.chzscout.backend.example.application.usecase.GetExampleUseCase;
import com.vatti.chzscout.backend.example.domain.dto.ExampleResponse;
import com.vatti.chzscout.backend.example.domain.entity.Example;
import com.vatti.chzscout.backend.example.exception.ExampleErrorCode;
import com.vatti.chzscout.backend.example.infrastructure.ExampleRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetExampleService implements GetExampleUseCase {

  private final ExampleRepository exampleRepository;

  @Override
  public ExampleResponse execute(Long id) {
    Example example =
        exampleRepository
            .findById(id)
            .orElseThrow(() -> new BusinessException(ExampleErrorCode.EXAMPLE_NOT_FOUND));

    return ExampleResponse.from(example);
  }

  @Override
  public List<ExampleResponse> executeAll() {
    return exampleRepository.findAll().stream().map(ExampleResponse::from).toList();
  }
}
