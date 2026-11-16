package com.irvinsegura.supportchat.workspaces.web;

import com.irvinsegura.supportchat.shared.web.TenantScopedChain;
import com.irvinsegura.supportchat.workspaces.WidgetWorkspace;
import com.irvinsegura.supportchat.workspaces.Workspaces;
import com.irvinsegura.supportchat.workspaces.domain.model.UnknownWidgetKeyException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Binds the workspace named by the widget key in {@code /api/v1/widget/{widgetKey}/...}, so everything the request
 * does runs inside that workspace's row-level security scope. Unknown keys end the request with 404.
 */
class WidgetTenantFilter extends OncePerRequestFilter {

    static final String PREFIX = "/api/v1/widget/";

    private final Workspaces workspaces;
    private final HandlerExceptionResolver exceptionResolver;

    WidgetTenantFilter(Workspaces workspaces, HandlerExceptionResolver exceptionResolver) {
        this.workspaces = workspaces;
        this.exceptionResolver = exceptionResolver;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !path(request).startsWith(PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Optional<WidgetWorkspace> workspace = widgetKey(path(request)).flatMap(workspaces::findByWidgetKey);
        if (workspace.isEmpty()) {
            exceptionResolver.resolveException(request, response, null, new UnknownWidgetKeyException());
            return;
        }
        TenantScopedChain.proceed(workspace.get().workspaceId(), chain, request, response);
    }

    static Optional<String> widgetKey(String path) {
        if (!path.startsWith(PREFIX)) {
            return Optional.empty();
        }
        int end = path.indexOf('/', PREFIX.length());
        String key = path.substring(PREFIX.length(), end < 0 ? path.length() : end);
        return key.isEmpty() ? Optional.empty() : Optional.of(key);
    }

    static String path(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length());
    }
}
