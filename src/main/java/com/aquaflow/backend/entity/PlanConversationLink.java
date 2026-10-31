package com.aquaflow.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "plan_conversation_links")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlanConversationLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plan_id", nullable = false)
    private Long planId;

    @Column(name = "session_id", nullable = false, length = 64)
    private String sessionId;

    @Column(name = "link_type", nullable = false, length = 50)
    private String linkType; // CREATED, REFERENCED, RESUMED

    @Column(name = "linked_at", nullable = false)
    private OffsetDateTime linkedAt;
}

