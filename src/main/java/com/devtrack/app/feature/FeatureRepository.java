package com.devtrack.app.feature;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface FeatureRepository extends JpaRepository<Feature, Long> {
    List<Feature> findByProjectIdOrderByStatusAscCreatedAtDesc(Long projectId);
    List<Feature> findByProjectIdAndTitleContainingIgnoreCase(Long projectId, String title);
    Optional<Feature> findFirstByTitleContainingIgnoreCaseOrderByIdAsc(String title);
}
