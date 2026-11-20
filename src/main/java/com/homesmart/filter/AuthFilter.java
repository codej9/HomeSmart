package com.homesmart.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Sends visitors without a valid login to /login. A session ends after the idle limit or 8 hours after sign-in.
 * A background poll (header {@code X-HomeSmart-Poll}) gets 401 instead of a redirect and does not count as activity.
 */
public class AuthFilter implements Filter {

    public static final String POLL_HEADER = "X-HomeSmart-Poll";
    public static final String USERNAME = "username";
    public static final String LOGIN_AT = "loginAt";
    public static final String LAST_SEEN_AT = "lastSeenAt";

    static final String IDLE_MINUTES = "homesmart.idleMinutes";
    private static final Duration MAX_SESSION = Duration.ofHours(8);
    private static final long DEFAULT_IDLE_MINUTES = 30;

    private final Clock clock;
    private Duration idleLimit = Duration.ofMinutes(DEFAULT_IDLE_MINUTES);

    public AuthFilter() {
        this(Clock.systemUTC());
    }

    AuthFilter(Clock clock) {
        this.clock = clock;
    }

    /** The JVM system property overrides the context-param. */
    @Override
    public void init(FilterConfig config) {
        String minutes = System.getProperty(IDLE_MINUTES, config.getServletContext().getInitParameter(IDLE_MINUTES));
        if (minutes != null) {
            idleLimit = Duration.ofMinutes(Long.parseLong(minutes.trim()));
        }
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest http = (HttpServletRequest) request;
        String path = RequestPaths.path(http);
        if (RequestPaths.isAsset(path) || RequestPaths.isLoginOrSetup(path)) {
            chain.doFilter(request, response);
            return;
        }
        HttpSession session = http.getSession(false);
        Instant now = clock.instant();
        if (session != null && session.getAttribute(USERNAME) != null && !isLive(session, now)) {
            session.invalidate();
            session = null;
        }
        if (session == null || session.getAttribute(USERNAME) == null) {
            reject(http, (HttpServletResponse) response, path);
            return;
        }
        if (http.getHeader(POLL_HEADER) == null) {
            session.setAttribute(LAST_SEEN_AT, now);
        }
        chain.doFilter(request, response);
    }

    private boolean isLive(HttpSession session, Instant now) {
        return session.getAttribute(LOGIN_AT) instanceof Instant loginAt
                && session.getAttribute(LAST_SEEN_AT) instanceof Instant lastSeenAt
                && Duration.between(lastSeenAt, now).compareTo(idleLimit) <= 0
                && Duration.between(loginAt, now).compareTo(MAX_SESSION) < 0;
    }

    private static void reject(HttpServletRequest request, HttpServletResponse response, String path)
            throws IOException {
        if (request.getHeader(POLL_HEADER) != null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        String login = request.getContextPath() + "/login";
        if ("GET".equals(request.getMethod())) {
            login += "?next=" + URLEncoder.encode(request.getContextPath() + path, StandardCharsets.UTF_8);
        }
        response.sendRedirect(login);
    }
}
