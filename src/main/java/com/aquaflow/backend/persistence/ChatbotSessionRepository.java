package com.aquaflow.backend.persistence;

import com.aquaflow.backend.entity.ChatbotSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatbotSessionRepository extends JpaRepository<ChatbotSession, Long> {
    Optional<ChatbotSession> findBySessionId(String sessionId);
    Optional<ChatbotSession> findTopByUserIdOrderByUpdatedAtDesc(String userId);

    List<ChatbotSession> findByUserIdAndIsArchivedOrderByLastActivityAtDesc(String userId, Boolean isArchived);
    List<ChatbotSession> findByUserIdOrderByLastActivityAtDesc(String userId);

    @Query("SELECT s FROM ChatbotSession s WHERE s.userId = :userId AND " +
           "(:fieldId IS NULL OR s.fieldId = :fieldId) AND " +
           "(:includeArchived = true OR s.isArchived = false) " +
           "ORDER BY s.lastActivityAt DESC")
    List<ChatbotSession> searchSessions(
            @Param("userId") String userId,
            @Param("fieldId") Long fieldId,
            @Param("includeArchived") boolean includeArchived
    );
}
