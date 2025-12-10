package com.vatti.chzscout.backend.common.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 연결 테이블(다대다 관계)용 기반 엔티티.
 *
 * <p>BaseEntity와 달리 Soft Delete를 적용하지 않습니다. 연결 테이블은 "관계"를 표현하므로 관계가 끊어지면 물리 삭제하는 것이 일반적인 패턴입니다.
 *
 * <p>사용 예: MemberTag, UserRole 등
 */
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseRelationEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;
}
