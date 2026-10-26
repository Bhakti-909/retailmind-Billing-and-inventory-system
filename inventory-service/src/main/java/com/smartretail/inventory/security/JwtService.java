package com.smartretail.inventory.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey key;

    public JwtService(@Value("${jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public boolean validateToken(String token) {
        try {
            Jws<Claims> claimsJws = Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return claimsJws.getPayload().getExpiration() != null
                    && claimsJws.getPayload().getExpiration().after(new Date());
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public String extractUsername(String token) {
        try {
            return Jwts.parser().verifyWith(key).build().parseSignedClaims(token)
                    .getPayload().getSubject();
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    public String extractRole(String token) {
        try {
            return Jwts.parser().verifyWith(key).build().parseSignedClaims(token)
                    .getPayload().get("role", String.class);
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    public Long extractBusinessId(String token) {
        try {
            return Jwts.parser().verifyWith(key).build().parseSignedClaims(token)
                    .getPayload().get("businessId", Long.class);
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }
}