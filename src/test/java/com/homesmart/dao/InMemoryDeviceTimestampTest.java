package com.homesmart.dao;

import com.homesmart.model.Device;
import com.homesmart.service.DeviceService;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InMemoryDeviceTimestampTest {

    private final DeviceService service = new DeviceService(new InMemoryDeviceDAO());

    @Test
    void successfulCommandsTimestampEachDevice() {
        for (Device device : service.getAllDevices()) {
            assertNull(device.getLastUpdated());
        }

        // A successful request records an update even when the target state already matches.
        service.execute(1, "off", null);
        service.execute(2, "set", "22");
        service.execute(3, "unlock", null);

        for (Device device : service.getAllDevices()) {
            assertNotNull(device.getLastUpdated());
        }
    }

    @Test
    void rejectedCommandPreservesTheLastSuccessfulTimestamp() {
        service.execute(2, "set", "22");
        Instant beforeRejection = service.getDevice(2).getLastUpdated();
        assertNotNull(beforeRejection);

        assertThrows(IllegalArgumentException.class, () -> service.execute(2, "set", "99"));

        assertEquals(beforeRejection, service.getDevice(2).getLastUpdated());
        assertEquals("22 °C", service.getDevice(2).getStatus());
    }
}
