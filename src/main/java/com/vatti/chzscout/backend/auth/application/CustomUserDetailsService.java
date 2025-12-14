package com.vatti.chzscout.backend.auth.application;

import com.vatti.chzscout.backend.auth.domain.CustomUserDetails;
import com.vatti.chzscout.backend.member.domain.entity.Member;
import com.vatti.chzscout.backend.member.infrastructure.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * JWT 토큰의 uuid로 Member를 조회하여 UserDetails를 반환하는 서비스.
 *
 * <p>Spring Security의 UserDetailsService를 구현하며, JwtAuthenticationFilter에서 인증 정보를 생성할 때 사용됩니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomUserDetailsService implements UserDetailsService {

  private static final String DEFAULT_ROLE = "USER";

  private final MemberRepository memberRepository;

  /**
   * uuid로 Member를 조회하여 CustomUserDetails 반환.
   *
   * @param uuid JWT 토큰에서 추출한 사용자 uuid
   * @return CustomUserDetails (Member 정보 포함)
   * @throws UsernameNotFoundException Member를 찾을 수 없는 경우
   */
  @Override
  public UserDetails loadUserByUsername(String uuid) throws UsernameNotFoundException {
    Member member =
        memberRepository
            .findByUuid(uuid)
            .orElseThrow(() -> new UsernameNotFoundException("Member not found: " + uuid));

    return new CustomUserDetails(member, DEFAULT_ROLE);
  }
}
