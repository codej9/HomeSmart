package com.homesmart.e2e;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.Select;

/** Create a schedule, see it listed, then delete it so later runs start clean. */
final class ScheduleE2E extends E2EBase {

    private static final String CREATE_FORM = "form:has([name='op'][value='create'])";

    @Test
    void scheduleIsCreatedListedAndDeleted() {
        signIn();
        open("/schedules");
        String name = "E2E light " + System.currentTimeMillis() % 1_000_000;

        WebElement form = find(CREATE_FORM);
        typeInto(By.name("name"), name);
        new Select(form.findElement(By.name("deviceId"))).selectByValue("1");
        new Select(form.findElement(By.name("activityType"))).selectByValue("TURN_OFF");
        js("arguments[0].value = arguments[1];", form.findElement(By.name("runAt")), "23:30");
        for (WebElement day : form.findElements(By.name("days"))) {
            if (!day.isSelected()) {
                day.click();
            }
        }
        submitFormOf(form.findElement(By.name("name")));
        waitForText(name);
        snapshot("schedule-created");

        removeSchedule(name);
        wait.until(d -> !bodyText().contains(name));
    }

    private void removeSchedule(String name) {
        // submitFormOf would submit the row's first button, Turn off.
        String xpath = "//tr[th[contains(., '" + name + "')]]//button[@name='op' and @value='delete']";
        driver.findElement(By.xpath(xpath)).click();
    }
}
