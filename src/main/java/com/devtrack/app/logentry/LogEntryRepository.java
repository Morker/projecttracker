package com.devtrack.app.logentry;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface LogEntryRepository extends JpaRepository<LogEntry, Long> {
    List<LogEntry> findByProjectIdOrderByEntryDateDescCreatedAtDesc(Long projectId);
    List<LogEntry> findTop5ByOrderByCreatedAtDesc();
}
