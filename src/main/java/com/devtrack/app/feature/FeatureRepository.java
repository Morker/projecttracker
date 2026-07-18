package com.devtrack.app.feature;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface FeatureRepository extends JpaRepository<Feature, Long> {
    List<Feature> findByProjectIdOrderByStatusAscCreatedAtDesc(Long projectId);
}
