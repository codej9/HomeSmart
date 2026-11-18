package com.homesmart.filter;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsrfFilterTest {

    private final Fakes.Response response = new Fakes.Response();
    private final Fakes.Chain chain = new Fakes.Chain();

    private Fakes.Request run(String method, String path, String sentToken) throws IOException, jakarta.servlet.ServletException {
        Fakes.Request request = new Fakes.Request(method, path);
        request.session = new Fakes.Session();
        request.session.attributes.put(CsrfFilter.TOKEN, "right-token");
        if (sentToken != null) {
            request.params.put("_csrf", sentToken);
        }
        new CsrfFilter().doFilter(request.proxy, response.proxy, chain.proxy);
        return request;
    }

    @Test
    void tokenIs32BytesAndDiffersEachTime() {
        String token = CsrfFilter.newToken();

        assertEquals(43, token.length());
        assertNotEquals(token, CsrfFilter.newToken());
    }

    @Test
    void matchesOnlyTheSameToken() {
        assertTrue(CsrfFilter.matches("abc", "abc"));
        assertFalse(CsrfFilter.matches("abc", "abd"));
        assertFalse(CsrfFilter.matches("abc", null));
    }

    @Test
    void firstRequestOfASessionGetsAToken() throws Exception {
        Fakes.Request request = new Fakes.Request("GET", "/login");
        new CsrfFilter().doFilter(request.proxy, response.proxy, chain.proxy);

        assertTrue(chain.called);
        assertEquals(43, ((String) request.session.attributes.get(CsrfFilter.TOKEN)).length());
    }

    @Test
    void getNeedsNoToken() throws Exception {
        run("GET", "/schedules", null);

        assertTrue(chain.called);
    }

    @Test
    void postWithMatchingTokenPasses() throws Exception {
        run("POST", "/schedules", "right-token");

        assertTrue(chain.called);
    }

    @Test
    void postWithoutTokenGets403() throws Exception {
        run("POST", "/schedules", null);

        assertEquals(403, response.status);
        assertFalse(chain.called);
        assertTrue(response.body.toString().contains("csrf-field.jspf"));
    }

    @Test
    void postWithWrongTokenGets403() throws Exception {
        run("POST", "/logout", "wrong");

        assertEquals(403, response.status);
        assertFalse(chain.called);
    }

    @Test
    void loginWithWrongTokenRedirectsBackWithExpiryMessage() throws Exception {
        Fakes.Request request = run("POST", "/login", "wrong");

        assertEquals("/homesmart/login", response.redirect);
        assertEquals(CsrfFilter.EXPIRED, request.session.attributes.get("flashError"));
        assertFalse(chain.called);
    }

    @Test
    void setupWithWrongTokenRedirectsBackToSetup() throws Exception {
        run("POST", "/setup", null);

        assertEquals("/homesmart/setup", response.redirect);
    }

    @Test
    void dashboardPostNeedsTheToken() throws Exception {
        run("POST", "/devices", null);

        assertEquals(403, response.status);
        assertFalse(chain.called);
    }

    @Test
    void assetsGetNoSession() throws Exception {
        Fakes.Request request = new Fakes.Request("GET", "/css/login.css");
        new CsrfFilter().doFilter(request.proxy, response.proxy, chain.proxy);

        assertTrue(chain.called);
        assertNull(request.session);
    }
}
