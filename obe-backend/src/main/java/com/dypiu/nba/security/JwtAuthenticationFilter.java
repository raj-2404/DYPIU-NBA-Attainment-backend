package com.dypiu.nba.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;
    private final CustomUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String jwt = getJwtFromRequest(request);

            if (StringUtils.hasText(jwt) && tokenProvider.validateToken(jwt)) {
                String username = tokenProvider.getUsernameFromJwt(jwt);
                String activeRole = tokenProvider.getActiveRoleFromJwt(jwt);
                String schoolId = tokenProvider.getSchoolIdFromJwt(jwt);
                String departmentId = tokenProvider.getDepartmentIdFromJwt(jwt);
                String masterProgrammeId = tokenProvider.getMasterProgrammeIdFromJwt(jwt);

                // Check optional header override
                String headerRole = request.getHeader("X-Active-Role");
                if (StringUtils.hasText(headerRole)) {
                    activeRole = headerRole.trim();
                }

                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                java.util.Collection<? extends org.springframework.security.core.GrantedAuthority> authorities;
                if (StringUtils.hasText(activeRole)) {
                    String cleanRole = activeRole.toUpperCase().startsWith("ROLE_") ? activeRole.toUpperCase() : "ROLE_" + activeRole.toUpperCase();
                    authorities = java.util.Collections.singletonList(new org.springframework.security.core.authority.SimpleGrantedAuthority(cleanRole));
                    request.setAttribute("ACTIVE_ROLE_OVERRIDE", activeRole.toUpperCase());
                } else {
                    authorities = userDetails.getAuthorities();
                }

                if (StringUtils.hasText(schoolId)) {
                    request.setAttribute("ACTIVE_SCHOOL_OVERRIDE", schoolId.trim());
                }
                if (StringUtils.hasText(departmentId)) {
                    request.setAttribute("ACTIVE_DEPT_OVERRIDE", departmentId.trim());
                }
                if (StringUtils.hasText(masterProgrammeId)) {
                    request.setAttribute("ACTIVE_PROG_OVERRIDE", masterProgrammeId.trim());
                }

                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        userDetails, null, authorities
                );
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception ex) {
            logger.error("Could not set user authentication in security context", ex);
        }

        filterChain.doFilter(request, response);
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
