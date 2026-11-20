package com.homesmart.servlet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ScheduleServletTest {

    @Test
    void selectsTheDeviceFromTheLightCard() {
        assertEquals(3, ScheduleServlet.selectedDeviceId("3"));
    }

    @Test
    void selectsNothingWithoutAValidDeviceId() {
        for (String parameter : new String[] {null, "", "abc", "1.5"}) {
            assertNull(ScheduleServlet.selectedDeviceId(parameter), String.valueOf(parameter));
        }
    }
}
