package com.vatti.chzscout.backend.ai.domain.entity;

import com.vatti.chzscout.backend.ai.infrastructure.type.VectorType;
import com.vatti.chzscout.backend.member.domain.entity.Member;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

@Entity
@Table(name = "member_embedding")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberEmbedding {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "member_id", nullable = false)
  private Member member;

  @Type(VectorType.class)
  @Column(name = "embedding", nullable = false, columnDefinition = "vector(1536)")
  private float[] embedding;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  private MemberEmbedding(Member member, float[] embedding) {
    this.member = member;
    this.embedding = embedding;
    this.updatedAt = LocalDateTime.now();
  }

  public static MemberEmbedding create(Member member, float[] embedding) {
    return new MemberEmbedding(member, embedding);
  }
}
