package com.vatti.chzscout.backend.tag.infrastructure;

import com.vatti.chzscout.backend.tag.domain.entity.Tag;
import java.util.List;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TagRepository extends JpaRepository<Tag, Long> {

  /** 활성 태그만 조회 (@SoftDelete 필터 적용) */
  List<Tag> findByNameIn(Set<String> names);

  /**
   * 삭제된 CUSTOM 태그 포함 전체 조회 (Native Query로 @SoftDelete 우회)
   *
   * @param names 조회할 태그 이름 목록
   * @return 삭제 여부와 관계없이 해당 이름의 모든 CUSTOM 태그
   */
  @Query(
      value = "SELECT * FROM tag WHERE name IN :names AND tag_type = 'CUSTOM'",
      nativeQuery = true)
  List<Tag> findCustomByNameInIncludingDeleted(@Param("names") Set<String> names);

  /**
   * 삭제된 CUSTOM 태그 복구
   *
   * @param names 복구할 태그 이름 목록
   * @return 복구된 태그 수
   */
  @Modifying
  @Query(
      value =
          "UPDATE tag SET deleted = false WHERE name IN :names AND tag_type = 'CUSTOM' AND deleted = true",
      nativeQuery = true)
  int restoreCustomByNames(@Param("names") Set<String> names);

  /**
   * 삭제된 CATEGORY 태그 포함 전체 조회 (Native Query로 @SoftDelete 우회)
   *
   * @param names 조회할 태그 이름 목록
   * @return 삭제 여부와 관계없이 해당 이름의 모든 CATEGORY 태그
   */
  @Query(
      value = "SELECT * FROM tag WHERE name IN :names AND tag_type = 'CATEGORY'",
      nativeQuery = true)
  List<Tag> findCategoryByNameInIncludingDeleted(@Param("names") Set<String> names);

  /**
   * 삭제된 CATEGORY 태그 복구
   *
   * @param names 복구할 태그 이름 목록
   * @return 복구된 태그 수
   */
  @Modifying
  @Query(
      value =
          "UPDATE tag SET deleted = false WHERE name IN :names AND tag_type = 'CATEGORY' AND deleted = true",
      nativeQuery = true)
  int restoreCategoryByNames(@Param("names") Set<String> names);
}
