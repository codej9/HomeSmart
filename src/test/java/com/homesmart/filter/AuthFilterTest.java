package com.homesmart.filter;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthFilterTest {

    private static final Instant START = Instant.parse("2026-11-18T12:00:00Z");

    private Clock clock = Clock.fixed(START, ZoneOffset.UTC);
    private final Fakes.Response response = new Fakes.Response();
    private final Fakes.Chain chain = new Fakes.Chain();

    private void run(Fakes.Request request) throws IOException, jakarta.servlet.ServletException {
        new AuthFilter(clock).doFilter(request.proxy, response.proxy, chain.proxy);
    }

    private Fakes.Request signedIn(String method, String path, Instant loginAt, Instant lastSeenAt) {
        Fakes.Request request = new Fakes.Request(method, path);
        request.session = new Fakes.Session();
        request.session.attributes.put(AuthFilter.USERNAME, "matt");
        request.session.attributes.put(AuthFilter.LOGIN_AT, loginAt);
        request.session.attributes.put(AuthFilter.LAST_SEEN_AT, lastSeenAt);
        return request;
    }

    @Test
    void pageWithoutLoginRedirectsToLoginWithNext() throws Exception {
        run(new Fakes.Request("GET", "/devices"));

        assertFalse(chain.called);
        assertEquals("/homesmart/login?next=%2Fhomesmart%2Fdevices", response.redirect);
    }

    @Test
    void postWithoutLoginRedirectsToPlainLogin() throws Exception {
        run(new Fakes.Request("POST", "/devices"));

        assertEquals("/homesmart/login", response.redirect);
    }

    @Test
    void pollWithoutLoginGets401() throws Exception {
        Fakes.Request request = new Fakes.Request("GET", "/panels/alerts");
        request.headers.put(AuthFilter.POLL_HEADER, "1");
        run(request);

        assertEquals(401, response.status);
        assertNull(response.redirect);
        assertFalse(chain.called);
    }

    @Test
    void loginSetupAndAssetsNeedNoLogin() throws Exception {
        for (String path : new String[] {"/login", "/setup", "/css/dashboard.css", "/js/alerts.js"}) {
            Fakes.Chain each = new Fakes.Chain();
            new AuthFilter(clock).doFilter(new Fakes.Request("GET", path).proxy, response.proxy, each.proxy);
            assertTrue(each.called, path);
        }
    }

    @Test
    void dotSegmentsDoNotReachTheAssetExemption() throws Exception {
        // the container resolves /css/../devices to servletPath /devices
        run(new Fakes.Request("GET", "/devices"));

        assertFalse(chain.called);
    }

    @Test
    void signedInRequestPassesAndRecordsActivity() throws Exception {
        Fakes.Request request = signedIn("GET", "/devices", START.minusSeconds(600), START.minusSeconds(60));
        run(request);

        assertTrue(chain.called);
        assertEquals(START, request.session.attributes.get(AuthFilter.LAST_SEEN_AT));
    }

    @Test
    void pollPassesButDoesNotResetIdleTime() throws Exception {
        Instant lastSeen = START.minusSeconds(60);
        Fakes.Request request = signedIn("GET", "/panels/alerts", START.minusSeconds(600), lastSeen);
        request.headers.put(AuthFilter.POLL_HEADER, "1");
        run(request);

        assertTrue(chain.called);
        assertEquals(lastSeen, request.session.attributes.get(AuthFilter.LAST_SEEN_AT));
    }

    @Test
    void idleSessionIsInvalidated() throws Exception {
        Fakes.Request request = signedIn("GET", "/devices", START.minus(Duration.ofHours(1)),
                START.minus(Duration.ofMinutes(31)));
        run(request);

        assertTrue(request.session.invalidated);
        assertFalse(chain.called);
        assertTrue(response.redirect.startsWith("/homesmart/login"));
    }

    @Test
    void idlePollGets401() throws Exception {
        Fakes.Request request = signedIn("GET", "/panels/alerts", START.minus(Duration.ofHours(1)),
                START.minus(Duration.ofMinutes(31)));
        request.headers.put(AuthFilter.POLL_HEADER, "1");
        run(request);

        assertTrue(request.session.invalidated);
        assertEquals(401, response.status);
    }

    @Test
    void sessionEndsEightHoursAfterLoginEvenWhenActive() throws Exception {
        Fakes.Request request = signedIn("GET", "/devices", START.minus(Duration.ofHours(8)), START.minusSeconds(5));
        run(request);

        assertTrue(request.session.invalidated);
        assertFalse(chain.called);
    }

    @Test
    void anonymousSessionKeepsItsTokenWhenRedirected() throws Exception {
        Fakes.Request request = new Fakes.Request("GET", "/devices");
        request.session = new Fakes.Session();
        request.session.attributes.put(CsrfFilter.TOKEN, "abc");
        run(request);

        assertFalse(request.session.invalidated);
        assertFalse(chain.called);
    }
}
