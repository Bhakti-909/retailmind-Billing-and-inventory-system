package com.smartretail.ai.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

public class JwtFilter extends OncePerRequestFilter {
	private final JwtService jwt;
	public JwtFilter(JwtService jwt) { this.jwt = jwt; }
	@Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
		String header = request.getHeader("Authorization");
		if (header != null && header.startsWith("Bearer ")) {
			String token = header.substring(7).trim();
			Long businessId = jwt.businessId(token);
			String username = jwt.username(token);
			if (!jwt.valid(token) || businessId == null || username == null) { response.sendError(401, "Invalid or expired token."); return; }
			SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(username, null, List.of()));
			request.setAttribute("businessId", businessId);
		}
		chain.doFilter(request, response);
	}
}
