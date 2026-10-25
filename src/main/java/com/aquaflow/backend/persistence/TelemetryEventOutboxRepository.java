package com.aquaflow.backend.persistence;

import com.aquaflow.backend.entity.TelemetryEventOutbox;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TelemetryEventOutboxRepository extends JpaRepository<TelemetryEventOutbox, Long> {

    List<TelemetryEventOutbox> findByStatusOrderByCreatedAtAsc(String status, Pageable pageable);

    Optional<TelemetryEventOutbox> findByEventId(String eventId);

    @Modifying
    @Query("UPDATE TelemetryEventOutbox o SET o.status = :status, o.dispatchedAt = :dispatchedAt WHERE o.id = :id")
    int updateStatus(@Param("id") Long id, @Param("status") String status, @Param("dispatchedAt") LocalDateTime dispatchedAt);

    @Modifying
    @Query("DELETE FROM TelemetryEventOutbox o WHERE o.status = 'DISPATCHED' AND o.dispatchedAt < :cutoff")
    int purgeDispatchedBefore(@Param("cutoff") LocalDateTime cutoff);
}

