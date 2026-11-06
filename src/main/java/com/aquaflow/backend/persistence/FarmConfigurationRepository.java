package com.aquaflow.backend.persistence;

import com.aquaflow.backend.entity.FarmConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FarmConfigurationRepository extends JpaRepository<FarmConfiguration, Long> {
    Optional<FarmConfiguration> findByFieldId(Long fieldId);
    Optional<FarmConfiguration> findByFieldIdAndIsActiveTrue(Long fieldId);
    Optional<FarmConfiguration> findFirstByIsActiveTrue();
}
