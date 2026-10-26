package com.smartretail.supplier.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;
import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Configuration
public class SecurityConfig {
    private final SecretKey key;
    public SecurityConfig(@Value("${jwt.secret}") String secret) { key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)); }

    @Bean CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:5173", "http://localhost:5174", "http://localhost:5175"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource(); source.registerCorsConfiguration("/**", config); return source;
    }

    @Bean SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http.csrf(AbstractHttpConfigurer::disable).cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint((request, response, exception) -> { response.setStatus(401); response.setContentType(MediaType.APPLICATION_JSON_VALUE); response.getWriter().write("{\"status\":401,\"message\":\"Authentication required. Please log in.\"}"); }))
                .authorizeHttpRequests(auth -> auth.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll().requestMatchers("/api/suppliers/**").authenticated().anyRequest().authenticated())
                .addFilterBefore(new JwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class).build();
    }

    private class JwtAuthenticationFilter extends OncePerRequestFilter {
        private final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
        @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
            if ("OPTIONS".equalsIgnoreCase(request.getMethod())) { chain.doFilter(request, response); return; }
            String header = request.getHeader("Authorization");
            if (header == null || !header.startsWith("Bearer ")) {
                log.warn("Rejected supplier request without a Bearer token: {} {}", request.getMethod(), request.getRequestURI());
                writeUnauthorized(response, "A login token was not sent with this supplier request. Please sign in again.");
                return;
            }
            try {
                Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(header.substring(7).trim()).getPayload();
                Long businessId = claims.get("businessId", Long.class); String username = claims.getSubject();
                if (businessId == null || username == null || username.isBlank() || claims.getExpiration() == null || claims.getExpiration().before(new java.util.Date())) throw new SecurityException("Missing required JWT claims.");
                request.setAttribute("businessId", businessId); request.setAttribute("username", username);
                org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(username, null, List.of()));
            } catch (Exception exception) {
                log.warn("Rejected supplier request with an invalid or expired Bearer token: {} {}", request.getMethod(), request.getRequestURI());
                writeUnauthorized(response, "The login token sent with this supplier request is invalid or expired. Please sign in again.");
                return;
            }
            chain.doFilter(request, response);
        }

        private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"status\":401,\"message\":\"" + message + "\"}");
        }
    }
}
