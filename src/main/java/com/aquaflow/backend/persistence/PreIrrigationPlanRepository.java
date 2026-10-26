package com.aquaflow.backend.persistence;

import com.aquaflow.backend.entity.PreIrrigationPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PreIrrigationPlanRepository extends JpaRepository<PreIrrigationPlan, Long> {
    Optional<PreIrrigationPlan> findByPlanCode(String planCode);
    List<PreIrrigationPlan> findByFieldIdOrderByCreatedAtDesc(Long fieldId);
    List<PreIrrigationPlan> findByUserIdOrderByCreatedAtDesc(String userId);
}

