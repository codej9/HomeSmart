package com.homesmart.e2e;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.WebElement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Out-of-range target: the browser blocks it, and the server rule rejects it when the browser check is off. */
final class ThermostatE2E extends E2EBase {

    private static final String TARGET_INPUT = "form:has(input[name='deviceId'][value='2']) input[name='value']";
    private static final String SET_BUTTON = "form:has(input[name='deviceId'][value='2']) button[name='action'][value='set']";

    @Test
    void targetAboveRangeIsRejected() {
        signIn();
        String before = find(".temp-display").getText();
        WebElement target = find(TARGET_INPUT);
        target.clear();
        target.sendKeys("33");
        assertTrue((Boolean) js("return arguments[0].validity.rangeOverflow;", target),
                "The browser range check must flag 33");

        // Without this the browser blocks the submit and the server rule never runs.
        js("arguments[0].form.noValidate = true;", target);
        find(SET_BUTTON).click();
        waitForText("Temperature must be between 10 and 32 °C");
        assertEquals(before, find(".temp-display").getText());
        snapshot("thermostat-rejected");
    }
}
