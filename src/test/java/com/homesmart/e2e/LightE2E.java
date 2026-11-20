package com.homesmart.e2e;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;

/** Light toggle: the button flips and the flash message names the device. */
final class LightE2E extends E2EBase {

    private static final String TOGGLE = "form:has(input[name='deviceId'][value='1']) button[name='action']";

    @Test
    void lightToggleFlipsButtonAndShowsMessage() {
        signIn();
        boolean wasOn = find(TOGGLE).getText().contains("Turn light off");
        find(TOGGLE).click();
        String verb = wasOn ? "off" : "on";
        waitForText("Living Room Light: Turned " + verb);
        String nextLabel = wasOn ? "Turn light on" : "Turn light off";
        wait.until(d -> d.findElement(By.cssSelector(TOGGLE)).getText().contains(nextLabel));
        snapshot("light-toggled");
    }
}
