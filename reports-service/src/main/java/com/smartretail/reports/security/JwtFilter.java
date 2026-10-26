package com.smartretail.reports.security;

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

	public JwtFilter(JwtService jwt) {
		this.jwt = jwt;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String authorization = request.getHeader("Authorization");
		if (authorization != null && authorization.startsWith("Bearer ")) {
			String token = authorization.substring(7).trim();
			if (!jwt.isValid(token) || jwt.username(token) == null) {
				response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid or expired token.");
				return;
			}
			SecurityContextHolder.getContext().setAuthentication(
					new UsernamePasswordAuthenticationToken(jwt.username(token), null, List.of()));
			request.setAttribute("businessId", jwt.businessId(token));
		}
		chain.doFilter(request, response);
	}
}
