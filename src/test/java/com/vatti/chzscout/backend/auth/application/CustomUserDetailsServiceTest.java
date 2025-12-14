package com.vatti.chzscout.backend.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.vatti.chzscout.backend.member.domain.entity.Member;
import com.vatti.chzscout.backend.member.infrastructure.MemberRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

  @InjectMocks private CustomUserDetailsService customUserDetailsService;

  @Mock private MemberRepository memberRepository;

  @Nested
  @DisplayName("loadUserByUsername 메서드")
  class LoadUserByUsernameTest {

    @Test
    @DisplayName("uuid로 Member 조회 성공 시 CustomUserDetails 반환")
    void loadUserByUsername_Success() {
      // given
      String uuid = "test-uuid-12345";
      Member member = Member.create("discord123", "testUser", "test@example.com");

      given(memberRepository.findByUuid(uuid)).willReturn(Optional.of(member));

      // when
      UserDetails result = customUserDetailsService.loadUserByUsername(uuid);

      // then
      assertThat(result).isNotNull();
      assertThat(result.getUsername()).isEqualTo(member.getUuid());
      assertThat(result.getAuthorities()).hasSize(1);
      assertThat(result.getAuthorities().iterator().next().getAuthority()).isEqualTo("ROLE_USER");
    }

    @Test
    @DisplayName("존재하지 않는 uuid로 조회 시 UsernameNotFoundException 발생")
    void loadUserByUsername_NotFound_ThrowsException() {
      // given
      String uuid = "non-existent-uuid";

      given(memberRepository.findByUuid(uuid)).willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(() -> customUserDetailsService.loadUserByUsername(uuid))
          .isInstanceOf(UsernameNotFoundException.class)
          .hasMessageContaining("Member not found");
    }
  }
}
