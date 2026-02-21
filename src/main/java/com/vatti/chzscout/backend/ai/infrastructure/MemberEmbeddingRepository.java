package com.vatti.chzscout.backend.ai.infrastructure;

import com.vatti.chzscout.backend.ai.domain.entity.MemberEmbedding;
import com.vatti.chzscout.backend.member.domain.entity.Member;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberEmbeddingRepository extends JpaRepository<MemberEmbedding, Long> {
  List<MemberEmbedding> findTop50ByMemberOrderByUpdatedAtDesc(Member member);
}
