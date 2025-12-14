package com.vatti.chzscout.backend.auth.domain;

import com.vatti.chzscout.backend.member.domain.entity.Member;
import java.util.Collection;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Member 엔티티를 감싸는 UserDetails 구현체.
 *
 * <p>Spring Security의 Authentication 객체에서 사용되며, Controller에서 @AuthenticationPrincipal로 주입받아 사용자 정보에
 * 접근할 수 있습니다.
 */
@Getter
@RequiredArgsConstructor
public class CustomUserDetails implements UserDetails {

  private final Member member;
  private final String role;

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return List.of(new SimpleGrantedAuthority("ROLE_" + role));
  }

  @Override
  public String getPassword() {
    return null; // OAuth 로그인이므로 비밀번호 없음
  }

  @Override
  public String getUsername() {
    return member.getUuid(); // uuid를 username으로 사용
  }

  @Override
  public boolean isAccountNonExpired() {
    return true;
  }

  @Override
  public boolean isAccountNonLocked() {
    return true;
  }

  @Override
  public boolean isCredentialsNonExpired() {
    return true;
  }

  @Override
  public boolean isEnabled() {
    return true;
  }
}
