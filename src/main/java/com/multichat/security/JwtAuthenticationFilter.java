package com.multichat.security;

import com.multichat.common.logging.LogContext;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtTokenService jwtTokenService;

    public JwtAuthenticationFilter(JwtTokenService jwtTokenService) {
        this.jwtTokenService = jwtTokenService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ") && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                Claims claims = jwtTokenService.parse(header.substring(7));
                List<SimpleGrantedAuthority> authorities = roles(claims).stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role)).toList();
                var authentication = new UsernamePasswordAuthenticationToken(claims.getSubject(), null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
                LogContext.putUserId(claims.getSubject());
            } catch (JwtException | IllegalArgumentException ignored) {
                SecurityContextHolder.clearContext();
            }
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            LogContext.putUserId(null);
        }
    }

    @SuppressWarnings("unchecked")
    private Collection<String> roles(Claims claims) {
        Object rawRoles = claims.get("roles");
        return rawRoles instanceof Collection<?> values
                ? values.stream().map(String::valueOf).toList()
                : List.of();
    }
}
