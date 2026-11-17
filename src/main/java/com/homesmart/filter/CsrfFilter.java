package com.homesmart.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Set;

/**
 * Gives each session a random token and rejects a changing request whose {@code _csrf} field does not match it.
 * Every form includes the token through {@code csrf-field.jspf}.
 */
public class CsrfFilter implements Filter {

    public static final String TOKEN = "csrfToken";
    static final String FIELD = "_csrf";
    static final String EXPIRED = "Your session expired. Try again.";
    static final String FORBIDDEN = "Request rejected: the form is missing its security token. "
            + "Developers: add <%@ include file=\"/WEB-INF/views/csrf-field.jspf\" %> inside the form.";

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");
    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest http = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        String path = RequestPaths.path(http);
        if (RequestPaths.isAsset(path)) {
            chain.doFilter(request, response);
            return;
        }
        HttpSession session = http.getSession();
        String token = (String) session.getAttribute(TOKEN);
        if (token == null) {
            token = newToken();
            session.setAttribute(TOKEN, token);
        }
        // The dashboard forms post here without the field until they carry it
        boolean exempt = SAFE_METHODS.contains(http.getMethod()) || path.equals("/devices");
        if (exempt || matches(token, http.getParameter(FIELD))) {
            chain.doFilter(request, response);
        } else if (RequestPaths.isLoginOrSetup(path)) {
            session.setAttribute("flashError", EXPIRED);
            httpResponse.sendRedirect(http.getContextPath() + path);
        } else {
            httpResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
            httpResponse.setContentType("text/plain;charset=UTF-8");
            httpResponse.getWriter().println(FORBIDDEN);
        }
    }

    /** 32 random bytes, Base64 URL encoded. */
    static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static boolean matches(String expected, String actual) {
        return actual != null && MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }
}
