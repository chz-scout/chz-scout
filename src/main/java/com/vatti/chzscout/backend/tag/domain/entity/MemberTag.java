package com.vatti.chzscout.backend.tag.domain.entity;

import com.vatti.chzscout.backend.common.domain.entity.BaseRelationEntity;
import com.vatti.chzscout.backend.member.domain.entity.Member;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원-태그 연결 엔티티.
 *
 * <p>BaseRelationEntity를 상속하여 Soft Delete 대신 Hard Delete를 사용합니다. 연결 테이블은 "관계"를 표현하므로 삭제 후 재생성이
 * 자유롭습니다.
 */
@Entity
@Getter
@Table(
    name = "member_tag",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_member_tag_member_id_tag_id",
          columnNames = {"member_id", "tag_id"})
    })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberTag extends BaseRelationEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "member_id", nullable = false)
  private Member member;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "tag_id", nullable = false)
  private Tag tag;

  private MemberTag(Member member, Tag tag) {
    this.member = member;
    this.tag = tag;
  }

  public static MemberTag create(Member member, Tag tag) {
    return new MemberTag(member, tag);
  }
}
