package com.aquaflow.backend.persistence;

import com.aquaflow.backend.entity.PlanStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PlanStatusHistoryRepository extends JpaRepository<PlanStatusHistory, Long> {
    List<PlanStatusHistory> findByPlanIdOrderByCreatedAtAsc(Long planId);
}

