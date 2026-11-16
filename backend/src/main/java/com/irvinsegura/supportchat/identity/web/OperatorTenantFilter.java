package com.irvinsegura.supportchat.identity.web;

import com.irvinsegura.supportchat.identity.OperatorClaims;
import com.irvinsegura.supportchat.shared.web.TenantScopedChain;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Binds the workspace an operator's access token was issued for, so the rest of the request runs inside that
 * workspace's row-level security scope. Requests without a token pass through unbound.
 */
class OperatorTenantFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwt
                && jwt.getToken().getClaimAsString(OperatorClaims.WORKSPACE) instanceof String workspace) {
            TenantScopedChain.proceed(UUID.fromString(workspace), chain, request, response);
            return;
        }
        chain.doFilter(request, response);
    }
}
