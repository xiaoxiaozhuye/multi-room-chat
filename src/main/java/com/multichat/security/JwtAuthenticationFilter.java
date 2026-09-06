package com.multichat.security;

import com.multichat.common.logging.LogContext;
import com.multichat.infrastructure.mapper.UserMapper;
import com.multichat.auth.entity.UserAccount;
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
import java.util.List;
import java.util.UUID;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtTokenService jwtTokenService;
    private final UserMapper userMapper;

    public JwtAuthenticationFilter(JwtTokenService jwtTokenService, UserMapper userMapper) {
        this.jwtTokenService = jwtTokenService;
        this.userMapper = userMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ") && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                Claims claims = jwtTokenService.parse(header.substring(7));
                UUID userId = UUID.fromString(claims.getSubject());
                UserAccount user = userMapper.findActiveById(userId).orElse(null);
                if (user == null) {
                    SecurityContextHolder.clearContext();
                    filterChain.doFilter(request, response);
                    return;
                }
                List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.role()));
                var authentication = new UsernamePasswordAuthenticationToken(userId.toString(), null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
                LogContext.putUserId(userId);
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

}
