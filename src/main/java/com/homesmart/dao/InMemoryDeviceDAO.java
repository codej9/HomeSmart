package com.homesmart.dao;

import com.homesmart.model.Device;
import com.homesmart.model.DoorLock;
import com.homesmart.model.Light;
import com.homesmart.model.Thermostat;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps device state in memory while the MySQL database is being connected.
 * State resets when the server restarts.
 */
public class InMemoryDeviceDAO implements DeviceDAO {

    private final Map<Integer, Device> devices = new ConcurrentHashMap<>();

    public InMemoryDeviceDAO() {
        save(new Light(1, "Living Room Light", false));
        save(new Thermostat(2, "Main Thermostat", 21));
        save(new DoorLock(3, "Front Door Lock", true));
    }

    @Override
    public List<Device> findAll() {
        return devices.values().stream()
                .sorted(Comparator.comparingInt(Device::getId))
                .toList();
    }

    @Override
    public Optional<Device> findById(int id) {
        return Optional.ofNullable(devices.get(id));
    }

    @Override
    public void update(Device device) {
        if (!devices.containsKey(device.getId())) {
            throw new IllegalArgumentException("No device with id " + device.getId());
        }
        save(device);
    }

    private void save(Device device) {
        devices.put(device.getId(), device);
    }
}
