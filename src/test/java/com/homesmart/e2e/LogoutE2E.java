package com.homesmart.e2e;

import org.junit.jupiter.api.Test;

/** Log out ends the session, so the dashboard redirects to login again. */
final class LogoutE2E extends E2EBase {

    @Test
    void logOutEndsSession() {
        signIn();
        find("form[action$='/logout'] button").click();
        wait.until(d -> d.getCurrentUrl().endsWith("/login"));

        open("/devices");
        wait.until(d -> d.getCurrentUrl().contains("/login"));
        snapshot("logged-out");
    }
}
