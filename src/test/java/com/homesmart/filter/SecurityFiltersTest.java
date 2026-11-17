package com.homesmart.filter;

import com.homesmart.listener.AppContextListener;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityFiltersTest {

    private final Fakes.Response response = new Fakes.Response();
    private final Fakes.Chain chain = new Fakes.Chain();

    @Test
    void pagesGetAllHeadersAndNoStore() throws Exception {
        new SecurityHeadersFilter().doFilter(new Fakes.Request("GET", "/devices").proxy, response.proxy, chain.proxy);

        assertTrue(chain.called);
        assertEquals(SecurityHeadersFilter.CONTENT_SECURITY_POLICY, response.headers.get("Content-Security-Policy"));
        assertEquals("nosniff", response.headers.get("X-Content-Type-Options"));
        assertEquals("same-origin", response.headers.get("Referrer-Policy"));
        assertEquals("no-store", response.headers.get("Cache-Control"));
    }

    @Test
    void assetsAreCacheable() throws Exception {
        new SecurityHeadersFilter().doFilter(new Fakes.Request("GET", "/css/theme.css").proxy, response.proxy, chain.proxy);

        assertNull(response.headers.get("Cache-Control"));
        assertEquals("nosniff", response.headers.get("X-Content-Type-Options"));
    }

    @Test
    void missingUsersTableAnswers503ExceptForAssets() throws Exception {
        Fakes.Request page = new Fakes.Request("GET", "/login");
        page.context.attributes.put(AppContextListener.USERS_TABLE_MISSING, true);
        new SchemaGuardFilter().doFilter(page.proxy, response.proxy, chain.proxy);

        assertEquals(503, response.status);
        assertFalse(chain.called);
        assertTrue(response.body.toString().contains("Accounts need the latest database schema."));

        Fakes.Request asset = new Fakes.Request("GET", "/css/dashboard.css");
        asset.context.attributes.put(AppContextListener.USERS_TABLE_MISSING, true);
        new SchemaGuardFilter().doFilter(asset.proxy, response.proxy, chain.proxy);

        assertTrue(chain.called);
    }

    @Test
    void presentUsersTableLetsRequestsThrough() throws Exception {
        new SchemaGuardFilter().doFilter(new Fakes.Request("GET", "/devices").proxy, response.proxy, chain.proxy);

        assertTrue(chain.called);
    }
}
