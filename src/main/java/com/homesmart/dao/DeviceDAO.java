package com.homesmart.dao;

import com.homesmart.model.Device;

import java.util.List;
import java.util.Optional;

/**
 * Storage contract for device state. The backend only talks to this interface,
 * so the in-memory version can be swapped for the MySQL version without
 * changing the service or servlet code.
 */
public interface DeviceDAO {

    /** All devices, ordered by id. */
    List<Device> findAll();

    Optional<Device> findById(int id);

    /** Saves the current state of an existing device. */
    void update(Device device);
}
