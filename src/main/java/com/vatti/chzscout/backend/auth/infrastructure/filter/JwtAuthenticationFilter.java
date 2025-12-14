package com.vatti.chzscout.backend.auth.infrastructure.filter;

import com.vatti.chzscout.backend.auth.application.JwtTokenProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * JWT 토큰을 검증하고 SecurityContext에 인증 정보를 설정하는 필터.
 *
 * <p>매 요청마다 Authorization 헤더에서 Bearer 토큰을 추출하고, 유효한 토큰인 경우 UserDetailsService를 통해 사용자 정보를 로드하여
 * SecurityContext에 저장합니다.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private static final String AUTHORIZATION_HEADER = "Authorization";
  private static final String BEARER_PREFIX = "Bearer ";

  private final JwtTokenProvider jwtTokenProvider;
  private final UserDetailsService userDetailsService;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    // 1. Authorization 헤더에서 토큰 추출
    String authorizationHeader = request.getHeader(AUTHORIZATION_HEADER);
    if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
      filterChain.doFilter(request, response);
      return;
    }

    // 2. Bearer 접두사 제거
    String accessToken = authorizationHeader.substring(BEARER_PREFIX.length());

    // 3. 토큰 유효성 검증 (서명 + 만료시간)
    if (!jwtTokenProvider.validateToken(accessToken)) {
      log.debug("Invalid or expired JWT token");
      filterChain.doFilter(request, response);
      return;
    }

    // 4. 토큰에서 uuid 추출 후 UserDetails 로드
    String uuid = jwtTokenProvider.getUuid(accessToken);
    UserDetails userDetails = userDetailsService.loadUserByUsername(uuid);

    // 5. SecurityContext에 인증 정보 설정
    UsernamePasswordAuthenticationToken authentication =
        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

    SecurityContextHolder.getContext().setAuthentication(authentication);

    // 6. 다음 필터로 진행
    filterChain.doFilter(request, response);
  }
}
