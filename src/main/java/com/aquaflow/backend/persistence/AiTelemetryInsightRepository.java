package com.aquaflow.backend.persistence;

import com.aquaflow.backend.entity.AiTelemetryInsight;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface AiTelemetryInsightRepository extends JpaRepository<AiTelemetryInsight, Long> {

    List<AiTelemetryInsight> findTop20ByOrderByCreatedAtDesc();

    Page<AiTelemetryInsight> findByFieldIdOrderByCreatedAtDesc(Long fieldId, Pageable pageable);

    Page<AiTelemetryInsight> findByZoneIdOrderByCreatedAtDesc(Long zoneId, Pageable pageable);

    List<AiTelemetryInsight> findByInsightTypeOrderByCreatedAtDesc(String insightType);

    @Modifying
    @Query("DELETE FROM AiTelemetryInsight i WHERE i.createdAt < :cutoff")
    int deleteInsightsOlderThan(@Param("cutoff") LocalDateTime cutoff);
}

