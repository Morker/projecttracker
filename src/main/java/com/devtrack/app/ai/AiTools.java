package com.devtrack.app.ai;

import com.devtrack.app.feature.Feature;
import com.devtrack.app.feature.FeatureRepository;
import com.devtrack.app.feature.FeatureStatus;
import com.devtrack.app.project.Project;
import com.devtrack.app.project.ProjectRepository;
import com.devtrack.app.project.ProjectStatus;
import com.devtrack.app.user.User;
import com.devtrack.app.user.UserRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AiTools {

    private static final Logger log = LoggerFactory.getLogger(AiTools.class);

    private final ProjectRepository projectRepository;
    private final FeatureRepository featureRepository;
    private final UserRepository userRepository;

    private final Map<String, Function<Map<String, Object>, String>> actions = new LinkedHashMap<>();
    private final List<String> actionDescriptions = new ArrayList<>();

    public AiTools(ProjectRepository projectRepository, FeatureRepository featureRepository, UserRepository userRepository) {
        this.projectRepository = projectRepository;
        this.featureRepository = featureRepository;
        this.userRepository = userRepository;
    }

    @PostConstruct
    public void init() {
        register("listProjects", "List all projects, optionally filter by status",
            p -> listProjects(str(p, "status")),
            "params:{\"status\":\"DONE\"}  // optional: IDEA, IN_PROGRESS, PAUSED, DONE, ARCHIVED");

        register("createProject", "Create a new project",
            p -> createProject(str(p, "name"), str(p, "description"), str(p, "techStack"), str(p, "status")),
            "params:{\"name\":\"Name\",\"description\":\"desc\",\"techStack\":\"Java, React\",\"status\":\"IN_PROGRESS\"}");

        register("updateProject", "Update a project's name, description, tech stack, or status (by ID or name)",
            p -> updateProject(str(p, "projectId"), str(p, "projectName"),
                str(p, "name"), str(p, "description"), str(p, "techStack"), str(p, "status")),
            "params:{\"projectName\":\"KrugerStore\",\"status\":\"DONE\"}");

        register("deleteProject", "Delete a project (by ID or name)",
            p -> deleteProject(str(p, "projectId"), str(p, "projectName")),
            "params:{\"projectName\":\"KrugerStore\"}");

        register("listFeatures", "List features of a project (by ID or name)",
            p -> listFeatures(str(p, "projectId"), str(p, "projectName")),
            "params:{\"projectName\":\"KrugerStore\"}");

        register("createFeature", "Create a feature in a project (by ID or name)",
            p -> createFeature(str(p, "projectId"), str(p, "projectName"),
                str(p, "title"), str(p, "description"), str(p, "status")),
            "params:{\"projectName\":\"KrugerStore\",\"title\":\"Login\",\"description\":\"user auth\",\"status\":\"PLANNED\"}");

        register("updateFeatureStatus", "Update a feature's status (by ID or title)",
            p -> updateFeatureStatus(str(p, "featureId"), str(p, "featureTitle"), str(p, "status")),
            "params:{\"featureTitle\":\"Login\",\"status\":\"DONE\"}");

        register("deleteFeature", "Delete a feature (by ID or title)",
            p -> deleteFeature(str(p, "featureId"), str(p, "featureTitle")),
            "params:{\"featureTitle\":\"Login\"}");
    }

    private void register(String name, String description, Function<Map<String, Object>, String> handler, String example) {
        actions.put(name, handler);
        actionDescriptions.add("- " + name + " — " + description + "\n  Example: {\"action\":\"" + name + "\"," + example + "}");
    }

    public String execute(String action, Map<String, Object> params) {
        Function<Map<String, Object>, String> handler = actions.get(action);
        if (handler == null) return "Unknown action: " + action;
        return handler.apply(params);
    }

    public List<String> getActionDescriptions() {
        return Collections.unmodifiableList(actionDescriptions);
    }

    // ==================== Helpers ====================

    private String str(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }

    public Project resolveProject(String projectId, String projectName) {
        if (projectId != null && !projectId.isBlank()) {
            try { return projectRepository.findById(Long.parseLong(projectId)).orElse(null); }
            catch (NumberFormatException ignored) {}
        }
        if (projectName != null && !projectName.isBlank()) {
            return projectRepository.findFirstByNameContainingIgnoreCaseOrderByNameAsc(projectName).orElse(null);
        }
        return null;
    }

    public Feature resolveFeature(String featureId, String featureTitle) {
        if (featureId != null && !featureId.isBlank()) {
            try { return featureRepository.findById(Long.parseLong(featureId)).orElse(null); }
            catch (NumberFormatException ignored) {}
        }
        if (featureTitle != null && !featureTitle.isBlank()) {
            return featureRepository.findFirstByTitleContainingIgnoreCaseOrderByIdAsc(featureTitle).orElse(null);
        }
        return null;
    }

    // ==================== Actions ====================

    public String listProjects(String status) {
        List<Project> projects;
        if (status != null && !status.isBlank()) {
            try {
                ProjectStatus ps = ProjectStatus.valueOf(status.toUpperCase());
                projects = projectRepository.findByStatusOrderByUpdatedAtDesc(ps);
            } catch (IllegalArgumentException e) {
                return "Invalid status: " + status + ". Use IDEA, IN_PROGRESS, PAUSED, DONE, or ARCHIVED.";
            }
        } else {
            projects = projectRepository.findAllByOrderByUpdatedAtDesc();
        }
        if (projects.isEmpty()) return "No projects" + (status != null ? " with status " + status : "") + ".";
        return projects.stream()
            .map(p -> "#" + p.getId() + " " + p.getName() + " (" + p.getStatus().getLabel() + ")" +
                      (p.getDescription() != null ? ": " + p.getDescription() : ""))
            .collect(Collectors.joining("\n"));
    }

    public String createProject(String name, String description, String techStack, String status) {
        User admin = userRepository.findByUsername("admin").orElse(null);
        if (admin == null) return "No admin user found. Cannot create project.";

        Project p = new Project();
        p.setName(name);
        p.setDescription(description != null && !description.isBlank() ? description : null);
        p.setTechStack(techStack != null && !techStack.isBlank() ? techStack : null);
        p.setOwner(admin);
        try {
            p.setStatus(status != null ? ProjectStatus.valueOf(status.toUpperCase()) : ProjectStatus.IN_PROGRESS);
        } catch (IllegalArgumentException e) {
            p.setStatus(ProjectStatus.IN_PROGRESS);
        }
        projectRepository.save(p);
        return "Created project \"" + name + "\" with status " + p.getStatus().getLabel() + ".";
    }

    @Transactional
    public String updateProject(String projectId, String projectName, String name, String description, String techStack, String status) {
        Project p = resolveProject(projectId, projectName);
        if (p == null) return "Project not found" + (projectName != null ? " matching \"" + projectName + "\"" : "") + ".";

        if (name != null && !name.isBlank()) p.setName(name);
        if (description != null) p.setDescription(description.isBlank() ? null : description);
        if (techStack != null) p.setTechStack(techStack.isBlank() ? null : techStack);
        if (status != null && !status.isBlank()) {
            try { p.setStatus(ProjectStatus.valueOf(status.toUpperCase())); }
            catch (IllegalArgumentException e) { return "Invalid status: " + status + ". Use IDEA, IN_PROGRESS, PAUSED, DONE, or ARCHIVED."; }
        }
        projectRepository.save(p);
        return "Updated project \"" + p.getName() + "\".";
    }

    @Transactional
    public String deleteProject(String projectId, String projectName) {
        Project p = resolveProject(projectId, projectName);
        if (p == null) return "Project not found" + (projectName != null ? " matching \"" + projectName + "\"" : "") + ".";
        String name = p.getName();
        projectRepository.deleteById(p.getId());
        return "Deleted project \"" + name + "\".";
    }

    public String listFeatures(String projectId, String projectName) {
        Project project = resolveProject(projectId, projectName);
        if (project == null) return "Project not found" + (projectName != null ? " matching \"" + projectName + "\"" : "") + ".";

        List<Feature> features = featureRepository.findByProjectIdOrderByStatusAscCreatedAtDesc(project.getId());
        if (features.isEmpty()) return "No features in project \"" + project.getName() + "\".";
        return "Features of \"" + project.getName() + "\":\n" + features.stream()
            .map(f -> "#" + f.getId() + " " + f.getTitle() + " (" + f.getStatus().getLabel() + ")" +
                      (f.getDescription() != null ? ": " + f.getDescription() : ""))
            .collect(Collectors.joining("\n"));
    }

    public String createFeature(String projectId, String projectName, String title, String description, String status) {
        Project project = resolveProject(projectId, projectName);
        if (project == null) return "Project not found" + (projectName != null ? " matching \"" + projectName + "\"" : "") + ".";

        Feature f = new Feature();
        f.setTitle(title);
        f.setDescription(description != null && !description.isBlank() ? description : null);
        f.setProject(project);
        try {
            f.setStatus(status != null ? FeatureStatus.valueOf(status.toUpperCase()) : FeatureStatus.PLANNED);
        } catch (IllegalArgumentException e) {
            f.setStatus(FeatureStatus.PLANNED);
        }
        if (f.getStatus() == FeatureStatus.DONE) f.setCompletedAt(LocalDate.now());
        featureRepository.save(f);
        return "Created feature \"" + title + "\" in project \"" + project.getName() + "\" with status " + f.getStatus().getLabel() + ".";
    }

    @Transactional
    public String updateFeatureStatus(String featureId, String featureTitle, String status) {
        Feature f = resolveFeature(featureId, featureTitle);
        if (f == null) return "Feature not found" + (featureTitle != null ? " matching \"" + featureTitle + "\"" : "") + ".";
        try {
            f.setStatus(FeatureStatus.valueOf(status.toUpperCase()));
        } catch (IllegalArgumentException e) {
            return "Invalid status: " + status + ". Use PLANNED, IN_PROGRESS, or DONE.";
        }
        if (f.getStatus() == FeatureStatus.DONE) f.setCompletedAt(LocalDate.now());
        else f.setCompletedAt(null);
        featureRepository.save(f);
        return "Updated feature \"" + f.getTitle() + "\" to " + f.getStatus().getLabel() + ".";
    }

    @Transactional
    public String deleteFeature(String featureId, String featureTitle) {
        Feature f = resolveFeature(featureId, featureTitle);
        if (f == null) return "Feature not found" + (featureTitle != null ? " matching \"" + featureTitle + "\"" : "") + ".";
        String title = f.getTitle();
        String pname = f.getProject().getName();
        featureRepository.deleteById(f.getId());
        return "Deleted feature \"" + title + "\" from project \"" + pname + "\".";
    }
}
