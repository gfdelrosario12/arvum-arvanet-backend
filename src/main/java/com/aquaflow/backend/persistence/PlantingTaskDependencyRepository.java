package com.aquaflow.backend.persistence;

import com.aquaflow.backend.entity.PlantingTaskDependency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlantingTaskDependencyRepository extends JpaRepository<PlantingTaskDependency, Long> {
    List<PlantingTaskDependency> findByTaskId(Long taskId);
    List<PlantingTaskDependency> findByDependsOnTaskId(Long dependsOnTaskId);
    Optional<PlantingTaskDependency> findByTaskIdAndDependsOnTaskId(Long taskId, Long dependsOnTaskId);
    void deleteByTaskIdAndDependsOnTaskId(Long taskId, Long dependsOnTaskId);
}

