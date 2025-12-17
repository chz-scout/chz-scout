package com.vatti.chzscout.backend.tag.domain.entity;

import com.vatti.chzscout.backend.common.domain.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "tag")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Tag extends BaseEntity {

  @Column(nullable = false, unique = true)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private TagType tagType;

  @Column(nullable = false)
  private int usageCount = 0;

  private Tag(String name, TagType tagType) {
    this.name = name;
    this.tagType = tagType;
  }

  public static Tag createCategory(String name) {
    return new Tag(name, TagType.CATEGORY);
  }

  public static Tag createCustom(String name) {
    return new Tag(name, TagType.CUSTOM);
  }

  public void increaseUsageCount() {
    this.usageCount++;
  }
}
