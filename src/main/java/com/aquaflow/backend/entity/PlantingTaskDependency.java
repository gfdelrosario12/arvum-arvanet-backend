package com.aquaflow.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "planting_task_dependencies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlantingTaskDependency {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(name = "depends_on_task_id", nullable = false)
    private Long dependsOnTaskId;

    @Builder.Default
    @Column(name = "dependency_type", nullable = false, length = 50)
    private String dependencyType = "REQUIRED"; // REQUIRED, CONDITIONAL

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}

