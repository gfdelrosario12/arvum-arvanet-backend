package com.aquaflow.backend.persistence;

import com.aquaflow.backend.entity.PlanConversationLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlanConversationLinkRepository extends JpaRepository<PlanConversationLink, Long> {
    List<PlanConversationLink> findByPlanId(Long planId);
    List<PlanConversationLink> findBySessionId(String sessionId);
    Optional<PlanConversationLink> findByPlanIdAndSessionId(Long planId, String sessionId);
    boolean existsByPlanIdAndSessionId(Long planId, String sessionId);
}

