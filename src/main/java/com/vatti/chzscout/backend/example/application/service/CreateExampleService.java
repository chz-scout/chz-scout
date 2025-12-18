package com.vatti.chzscout.backend.example.application.service;

import com.vatti.chzscout.backend.common.exception.BusinessException;
import com.vatti.chzscout.backend.example.application.usecase.CreateExampleUseCase;
import com.vatti.chzscout.backend.example.domain.dto.CreateExampleRequest;
import com.vatti.chzscout.backend.example.domain.dto.ExampleResponse;
import com.vatti.chzscout.backend.example.domain.entity.Example;
import com.vatti.chzscout.backend.example.exception.ExampleErrorCode;
import com.vatti.chzscout.backend.example.infrastructure.ExampleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CreateExampleService implements CreateExampleUseCase {

  private final ExampleRepository exampleRepository;

  @Override
  @Transactional
  public ExampleResponse execute(CreateExampleRequest request) {
    if (exampleRepository.existsByEmail(request.email())) {
      throw new BusinessException(ExampleErrorCode.EXAMPLE_ALREADY_EXISTS);
    }

    Example example = Example.create(request.name(), request.email());
    Example savedExample = exampleRepository.save(example);

    return ExampleResponse.from(savedExample);
  }
}
