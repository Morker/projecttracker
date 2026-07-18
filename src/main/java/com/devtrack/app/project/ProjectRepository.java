package com.devtrack.app.project;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    List<Project> findAllByOrderByUpdatedAtDesc();

    List<Project> findByStatusOrderByUpdatedAtDesc(ProjectStatus status);

    @Query("SELECT p FROM Project p WHERE " +
           "LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(p.techStack) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "ORDER BY p.updatedAt DESC")
    List<Project> search(@Param("q") String query);

    long countByStatus(ProjectStatus status);
}
