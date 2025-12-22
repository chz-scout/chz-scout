package com.vatti.chzscout.backend.member.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.vatti.chzscout.backend.member.domain.dto.MemberResponse;
import com.vatti.chzscout.backend.member.domain.entity.Member;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("MemberService 단위 테스트")
class MemberServiceTest {

  private MemberService memberService;

  @BeforeEach
  void setUp() {
    memberService = new MemberService();
  }

  @Test
  @DisplayName("getCurrentMember - Member 엔티티를 MemberResponse DTO로 변환한다")
  void getCurrentMemberReturnsMemberResponse() {
    // given
    Member member = Member.create("discord-123456", "테스트유저", "test@example.com");

    // when
    MemberResponse response = memberService.getCurrentMember(member);

    // then
    assertThat(response.uuid()).isEqualTo(member.getUuid());
    assertThat(response.nickname()).isEqualTo("테스트유저");
    assertThat(response.discordId()).isEqualTo("discord-123456");
  }

  @Test
  @DisplayName("getCurrentMember - 이메일 없는 Member도 정상적으로 변환한다")
  void getCurrentMemberWithoutEmail() {
    // given
    Member member = Member.create("discord-789", "이메일없는유저", null);

    // when
    MemberResponse response = memberService.getCurrentMember(member);

    // then
    assertThat(response.uuid()).isEqualTo(member.getUuid());
    assertThat(response.nickname()).isEqualTo("이메일없는유저");
    assertThat(response.discordId()).isEqualTo("discord-789");
  }
}
