package com.homesmart.e2e;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

/**
 * Unlock the door, wait for the alert past the one-minute evidence threshold,
 * then lock from the alert and check it clears.
 */
final class DoorAlertE2E extends E2EBase {

    private static final String DOOR_BUTTON = "form:has(input[name='deviceId'][value='3']) button[name='action']";
    private static final String ALERTS = ".hs-alerts";
    private static final Duration ALERT_WAIT = Duration.ofSeconds(150);

    @Test
    void unlockedDoorAlertAppearsAndLockClearsIt() {
        signIn();
        setDoorLocked(true);
        setDoorLocked(false);

        new WebDriverWait(driver, ALERT_WAIT).ignoring(StaleElementReferenceException.class)
                .pollingEvery(Duration.ofSeconds(5))
                .until(d -> d.findElement(By.cssSelector(ALERTS)).getText().contains("has been unlocked for"));
        snapshot("door-alert");

        find(ALERTS + " button").click();
        wait.until(d -> !d.findElement(By.cssSelector(ALERTS)).getText().contains("unlocked"));
        snapshot("door-alert-cleared");
    }

    private void setDoorLocked(boolean locked) {
        String action = locked ? "Lock door" : "Unlock door";
        String result = locked ? "Unlock door" : "Lock door";
        boolean[] clicked = {false};
        wait.until(d -> {
            String label = d.findElement(By.cssSelector(DOOR_BUTTON)).getText();
            if (label.contains(result)) {
                return true;
            }
            if (label.contains(action) && !clicked[0]) {
                d.findElement(By.cssSelector(DOOR_BUTTON)).click();
                clicked[0] = true;
            }
            return false;
        });
    }
}
