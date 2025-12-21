package com.vatti.chzscout.backend.tag.application.service;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import com.vatti.chzscout.backend.stream.domain.AllFieldLiveDto;
import com.vatti.chzscout.backend.tag.application.usecase.TagUseCase;
import com.vatti.chzscout.backend.tag.domain.dto.TagAutocompleteResponse;
import com.vatti.chzscout.backend.tag.domain.entity.Tag;
import com.vatti.chzscout.backend.tag.domain.entity.TagType;
import com.vatti.chzscout.backend.tag.infrastructure.TagRepository;
import com.vatti.chzscout.backend.tag.infrastructure.redis.TagAutocompleteRedisStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class TagService implements TagUseCase {

  private final TagRepository tagRepository;
  private final TagAutocompleteRedisStore tagAutocompleteRedisStore;

  @Override
  public void extractAndSaveTag(List<AllFieldLiveDto> streams) {
    // 1. 스트림 목록에서 태그 및 카테고리 별 등장 횟수 집계
    Map<String, Long> tagCountMap =
        streams.stream()
            .flatMap(stream -> stream.tags().stream())
            .filter(tag -> tag != null && !tag.isBlank())
            .collect(Collectors.groupingBy(identity(), Collectors.counting()));

    Map<String, Long> categoryCountMap =
        streams.stream()
            .map(AllFieldLiveDto::liveCategoryValue)
            .filter(category -> category != null && !category.isBlank())
            .collect(Collectors.groupingBy(identity(), Collectors.counting()));

    // 디버깅: 첫 번째 스트림의 실제 데이터 확인
    if (!streams.isEmpty()) {
      AllFieldLiveDto sample = streams.get(0);
      log.info(
          "Sample stream - title: {}, tags: {}, liveCategory: {}, categoryType: {}",
          sample.liveTitle(),
          sample.tags(),
          sample.liveCategory(),
          sample.categoryType());
    }

    log.info("Collected tags: {} (count: {})", tagCountMap.keySet(), tagCountMap.size());
    log.info(
        "Collected categories: {} (count: {})", categoryCountMap.keySet(), categoryCountMap.size());

    if (tagCountMap.isEmpty() && categoryCountMap.isEmpty()) {
      return;
    }

    // 2. DB에서 태그 조회 (삭제된 태그 포함) - 빈 Set은 쿼리 안 함
    Map<String, Tag> allTags =
        tagCountMap.isEmpty()
            ? Map.of()
            : tagRepository.findCustomByNameInIncludingDeleted(tagCountMap.keySet()).stream()
                .collect(toMap(Tag::getName, identity()));

    Map<String, Tag> allCategories =
        categoryCountMap.isEmpty()
            ? Map.of()
            : tagRepository.findCategoryByNameInIncludingDeleted(categoryCountMap.keySet()).stream()
                .collect(toMap(Tag::getName, identity()));

    // 3. 태그 복구, 업데이트, 생성 처리
    processTagsAndSave(tagCountMap, allTags, TagType.CUSTOM);
    processTagsAndSave(categoryCountMap, allCategories, TagType.CATEGORY);
  }

  @Override
  public List<TagAutocompleteResponse> searchAutocomplete(
      String prefix, TagType tagType, int limit) {
    return tagAutocompleteRedisStore.findByPrefix(prefix, tagType, limit).stream()
        .map(
            member ->
                TagAutocompleteResponse.of(
                    tagAutocompleteRedisStore.extractTagName(member),
                    tagAutocompleteRedisStore.extractUsageCount(member)))
        .sorted((a, b) -> Long.compare(b.usageCount(), a.usageCount()))
        .toList();
  }

  /**
   * DB에 저장된 태그를 Redis 자동완성 캐시에 전체 갱신합니다.
   *
   * <p>기존 Redis 데이터를 삭제하고 DB의 최신 태그로 교체합니다.
   */
  @Override
  public void refreshAutocompleteCache() {
    List<Tag> categoryList = tagRepository.findAllCategoryTags();
    List<Tag> tagList = tagRepository.findAllCustomTags();

    tagAutocompleteRedisStore.saveAll(categoryList, TagType.CATEGORY);
    tagAutocompleteRedisStore.saveAll(tagList, TagType.CUSTOM);

    log.info(
        "Refreshed autocomplete cache - categories: {}, tags: {}",
        categoryList.size(),
        tagList.size());
  }

  private void processTagsAndSave(
      Map<String, Long> tagCountMap, Map<String, Tag> allTags, TagType tagType) {
    if (tagCountMap.isEmpty()) {
      return;
    }

    // 삭제된 태그 복구 (Native Query로 deleted = false 처리)
    Set<String> allTagNames = allTags.keySet();
    restoreByTagType(allTagNames, tagType);

    List<Tag> newTags = new ArrayList<>();
    for (var entry : tagCountMap.entrySet()) {
      String name = entry.getKey();
      Long tagCount = entry.getValue();

      if (allTagNames.contains(name)) {
        // 기존 태그 (활성 또는 복구됨): usageCount 증가
        Tag tag = allTags.get(name);
        tag.increaseUsageCount(tagCount);
      } else {
        // 신규 태그: 새로 생성
        newTags.add(createTagByType(name, tagCount, tagType));
      }
    }

    log.info(
        "Saving {} new tags of type {}: {}",
        newTags.size(),
        tagType,
        newTags.stream().map(Tag::getName).toList());
    tagRepository.saveAll(newTags);
  }

  private void restoreByTagType(Set<String> names, TagType tagType) {
    if (names.isEmpty()) {
      return;
    }
    switch (tagType) {
      case CUSTOM -> tagRepository.restoreCustomByNames(names);
      case CATEGORY -> tagRepository.restoreCategoryByNames(names);
    }
  }

  private Tag createTagByType(String name, Long usageCount, TagType tagType) {
    return switch (tagType) {
      case CUSTOM -> Tag.createCustom(name, usageCount);
      case CATEGORY -> Tag.createCategory(name, usageCount);
    };
  }
}
