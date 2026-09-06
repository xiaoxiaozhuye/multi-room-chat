package com.multichat.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtTokenService {
    private final JwtProperties properties;
    private final SecretKey signingKey;

    public JwtTokenService(JwtProperties properties) {
        this.properties = properties;
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(properties.secret()));
    }

    public String createAccessToken(UUID userId, Collection<String> roles) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(properties.issuer())
                .subject(userId.toString())
                .claim("roles", roles)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.accessTokenTtl())))
                .signWith(signingKey)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser().verifyWith(signingKey).requireIssuer(properties.issuer()).build()
                .parseSignedClaims(token).getPayload();
    }
}
