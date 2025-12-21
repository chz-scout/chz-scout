package com.vatti.chzscout.backend.tag.infrastructure.listener;

import static org.mockito.Mockito.verify;

import com.vatti.chzscout.backend.tag.application.usecase.TagUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TagCacheRefreshListenerTest {

  @Mock private TagUseCase tagUseCase;

  @InjectMocks private TagCacheRefreshListener tagCacheRefreshListener;

  @Test
  @DisplayName("이벤트 수신 시 tagUseCase.refreshAutocompleteCache()를 호출한다")
  void callsRefreshAutocompleteCacheOnEvent() {
    // when
    tagCacheRefreshListener.onStreamCacheRefreshed();

    // then
    verify(tagUseCase).refreshAutocompleteCache();
  }
}
