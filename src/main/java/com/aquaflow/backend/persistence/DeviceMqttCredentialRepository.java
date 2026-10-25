package com.aquaflow.backend.persistence;

import com.aquaflow.backend.entity.DeviceMqttCredential;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DeviceMqttCredentialRepository extends JpaRepository<DeviceMqttCredential, Long> {

    Optional<DeviceMqttCredential> findByDeviceId(String deviceId);

    Optional<DeviceMqttCredential> findByUsername(String username);

    boolean existsByDeviceId(String deviceId);
}

