package com.aquaflow.backend.persistence;

import com.aquaflow.backend.entity.PostIrrigationOutcome;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PostIrrigationOutcomeRepository extends JpaRepository<PostIrrigationOutcome, Long> {
    List<PostIrrigationOutcome> findByFieldIdOrderByRecordedAtDesc(Long fieldId);
    List<PostIrrigationOutcome> findByPlanId(Long planId);
    List<PostIrrigationOutcome> findByIrrigationCommandId(String irrigationCommandId);
}

