package com.vatti.chzscout.backend.member.infrastructure;

import com.vatti.chzscout.backend.member.domain.entity.Member;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {

  Optional<Member> findByDiscordId(String discordId);

  Optional<Member> findByUuid(String uuid);
}
