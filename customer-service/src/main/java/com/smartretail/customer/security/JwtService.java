package com.smartretail.customer.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtService {
    private final SecretKey key;
    public JwtService(@Value("${jwt.secret}") String secret) { key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)); }
    private Claims claims(String token) { return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload(); }
    public boolean valid(String token) { try { return claims(token).getExpiration().after(new Date()); } catch (JwtException | IllegalArgumentException exception) { return false; } }
    public String username(String token) { try { return claims(token).getSubject(); } catch (JwtException | IllegalArgumentException exception) { return null; } }
    public Long businessId(String token) { try { return claims(token).get("businessId", Long.class); } catch (JwtException | IllegalArgumentException exception) { return null; } }
}
