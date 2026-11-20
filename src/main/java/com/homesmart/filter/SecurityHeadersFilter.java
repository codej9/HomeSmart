package com.homesmart.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/** Adds the security headers to every response. Pages are not cached, so a signed-out browser cannot replay them. */
public class SecurityHeadersFilter implements Filter {

    static final String CONTENT_SECURITY_POLICY = "default-src 'self'; script-src 'self'; style-src 'self'; "
            + "img-src 'self' data:; form-action 'self'; frame-ancestors 'none'; base-uri 'none'; object-src 'none'";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletResponse http = (HttpServletResponse) response;
        http.setHeader("Content-Security-Policy", CONTENT_SECURITY_POLICY);
        http.setHeader("X-Content-Type-Options", "nosniff");
        http.setHeader("Referrer-Policy", "same-origin");
        if (!RequestPaths.isAsset(RequestPaths.path((HttpServletRequest) request))) {
            http.setHeader("Cache-Control", "no-store");
        }
        chain.doFilter(request, response);
    }
}
