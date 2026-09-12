package com.cathy.frauddetection.auth;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Reads the bearer token on each request and, if it verifies, records who the
 * caller is in the SecurityContext.
 *
 * <p>Authentication only. This filter never rejects a request: a missing or
 * invalid token simply leaves the context empty, and the authorization filter
 * later in the chain decides whether that is acceptable for the requested URL.
 * Returning 401 from here would also block the login endpoint, which by
 * definition is called without a token.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        extractToken(request)
                .flatMap(jwtService::parse)
                .ifPresent(claims -> authenticate(claims, request));

        // Always continues, whatever happened above.
        filterChain.doFilter(request, response);
    }

    private java.util.Optional<String> extractToken(HttpServletRequest request) {
        String header = request.getHeader(HEADER);
        if (header == null || !header.startsWith(PREFIX)) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(header.substring(PREFIX.length()));
    }

    private void authenticate(Claims claims, HttpServletRequest request) {
        String username = claims.getSubject();
        String role = claims.get(JwtService.ROLE_CLAIM, String.class);
        if (username == null || role == null) {
            return;
        }

        // Credentials are null: the signature already proved identity, so there
        // is no password to carry, and holding one here would risk logging it.
        var authentication = new UsernamePasswordAuthenticationToken(
                username,
                null,
                List.of(new SimpleGrantedAuthority(Role.valueOf(role).authority())));
        authentication.setDetails(
                new WebAuthenticationDetailsSource().buildDetails(request));

        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
