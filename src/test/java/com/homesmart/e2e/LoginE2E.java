package com.homesmart.e2e;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;

/** Sign-in, the generic failure text, and the redirect for a signed-out visitor. */
final class LoginE2E extends E2EBase {

    @Test
    void signInOpensDashboard() {
        signIn();
        waitForText("My devices");
        snapshot("login-dashboard");
    }

    @Test
    void wrongPasswordShowsGenericError() {
        open("/login");
        typeInto(By.name("username"), USER);
        typeInto(By.name("password"), "wrong-password-value");
        submitFormOf(driver.findElement(By.name("password")));
        waitForText("Username or password is incorrect.");
        snapshot("login-wrong-password");
    }

    @Test
    void signedOutVisitorIsSentToLogin() {
        open("/devices");
        wait.until(d -> d.getCurrentUrl().contains("/login"));
    }
}
