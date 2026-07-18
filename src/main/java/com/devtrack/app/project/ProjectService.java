package com.devtrack.app.project;

import com.devtrack.app.user.User;
import com.devtrack.app.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;

    public List<Project> findAll() {
        return projectRepository.findAllByOrderByUpdatedAtDesc();
    }

    public List<Project> findByStatus(ProjectStatus status) {
        return projectRepository.findByStatusOrderByUpdatedAtDesc(status);
    }

    public List<Project> search(String query) {
        return projectRepository.search(query);
    }

    public Project findById(Long id) {
        return projectRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Project not found: " + id));
    }

    @Transactional
    public Project save(Project project, String ownerUsername) {
        if (project.getOwner() == null) {
            User owner = userRepository.findByUsername(ownerUsername)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
            project.setOwner(owner);
        }
        return projectRepository.save(project);
    }

    @Transactional
    public void delete(Long id) {
        projectRepository.deleteById(id);
    }

    public DashboardStats getDashboardStats() {
        return new DashboardStats(
            projectRepository.count(),
            projectRepository.countByStatus(ProjectStatus.IN_PROGRESS),
            projectRepository.countByStatus(ProjectStatus.DONE),
            projectRepository.countByStatus(ProjectStatus.IDEA)
        );
    }

    public record DashboardStats(long total, long inProgress, long done, long ideas) {}
}
