package com.irvinsegura.supportchat.shared.web;

import com.irvinsegura.supportchat.shared.tenancy.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.io.IOException;
import java.util.UUID;

/**
 * Continues a servlet filter chain with a workspace bound, for the filters that work out which workspace a request
 * belongs to.
 */
public final class TenantScopedChain {

    private TenantScopedChain() {
    }

    public static void proceed(UUID workspaceId, FilterChain chain, ServletRequest request, ServletResponse response)
            throws IOException, ServletException {
        try {
            TenantContext.callChecked(workspaceId, () -> {
                chain.doFilter(request, response);
                return null;
            });
        } catch (IOException | ServletException | RuntimeException | Error e) {
            throw e;
        } catch (Exception e) {
            // doFilter declares nothing else; kept so the compiler accepts the generic callable
            throw new ServletException(e);
        }
    }
}
