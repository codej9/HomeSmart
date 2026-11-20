package com.homesmart.e2e;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Checks the top-bar brand mark on every page that renders the top bar. */
class BrandMarkE2E extends E2EBase {

    private static final String BRAND_MARK = "house-duotone.svg";

    @Test
    void brandMarkRendersOnEveryPage() {
        Map<String, String> marks = new LinkedHashMap<>();
        open("/login");
        marks.put("/login", brandMark());
        signIn();
        ((ChromeDriver) driver).executeCdpCommand("Emulation.setDeviceMetricsOverride",
                Map.of("width", 1280, "height", 800, "deviceScaleFactor", 1, "mobile", false));
        snapshot("brand-mark-devices");
        marks.put("/devices", brandMark());
        for (String path : List.of("/activity", "/schedules")) {
            open(path);
            marks.put(path, brandMark());
        }
        Map<String, String> expected = new LinkedHashMap<>(marks);
        expected.replaceAll((path, mark) -> BRAND_MARK);
        assertEquals(expected, marks, "brand mark on each page");
    }

    private String brandMark() {
        WebElement icon = find(".brand-mark img");
        boolean loaded = (Boolean) js("return arguments[0].complete && arguments[0].naturalWidth > 0;", icon);
        return loaded ? icon.getAttribute("src").replaceAll(".*/", "") : "icon not loaded";
    }
}
