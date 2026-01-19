package com.vatti.chzscout.backend.ai.application;

import com.vatti.chzscout.backend.ai.domain.entity.StreamEmbedding;
import com.vatti.chzscout.backend.ai.infrastructure.StreamEmbeddingRepository;
import com.vatti.chzscout.backend.stream.domain.AllFieldLiveDto;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 방송 임베딩 동기화 서비스.
 *
 * <p>변경된 방송의 임베딩만 삭제 후 재생성하는 단순한 전략을 사용합니다.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class StreamEmbeddingSyncService {

  private final EmbeddingService embeddingService;
  private final StreamEmbeddingRepository streamEmbeddingRepository;

  /**
   * 변경된 방송의 임베딩을 동기화합니다.
   *
   * <p>1. 변경된 채널의 기존 임베딩 삭제
   *
   * <p>2. 새 임베딩 생성 및 저장
   *
   * @param changedStreams 신규 또는 변경된 방송 목록
   * @param changedChannelIds 변경된 채널 ID 목록
   */
  @Transactional
  public void syncEmbeddings(
      List<AllFieldLiveDto> changedStreams,
      Set<String> changedChannelIds,
      Set<String> endedChannelIds) {
    if (changedChannelIds.isEmpty() && endedChannelIds.isEmpty()) {
      log.debug("변경/종료된 방송 없음, 동기화 스킵");
      return;
    }

    log.info("임베딩 동기화 시작 - 변경: {}개, 종료: {}개", changedChannelIds.size(), endedChannelIds.size());

    // 1. 변경 + 종료 채널의 임베딩 삭제 (한 번에 처리)
    Set<String> allDeleteIds = new HashSet<>(changedChannelIds);
    allDeleteIds.addAll(endedChannelIds);
    if (!allDeleteIds.isEmpty()) {
      streamEmbeddingRepository.deleteByChannelIdIn(List.copyOf(allDeleteIds));
      log.debug("임베딩 삭제 완료 - {}개", allDeleteIds.size());
    }

    // 2. 새 임베딩 생성 및 저장
    List<StreamEmbedding> newEmbeddings = embeddingService.createEmbeddingsBatch(changedStreams);
    streamEmbeddingRepository.saveAll(newEmbeddings);

    log.info("임베딩 동기화 완료 - {}개 저장", newEmbeddings.size());
  }
}
