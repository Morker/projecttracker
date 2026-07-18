package com.devtrack.app.feature;

import com.devtrack.app.project.Project;
import com.devtrack.app.project.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import jakarta.validation.Valid;

import java.time.LocalDate;
import java.util.List;

@Controller
@RequestMapping("/projects/{projectId}/features")
@RequiredArgsConstructor
public class FeatureController {

    private final FeatureRepository featureRepository;
    private final ProjectService projectService;
    private final FeatureImportService featureImportService;

    @GetMapping("/new")
    public String newForm(@PathVariable Long projectId, Model model) {
        if (!model.containsAttribute("feature")) {
            model.addAttribute("feature", new Feature());
        }
        model.addAttribute("project", projectService.findById(projectId));
        model.addAttribute("statuses", FeatureStatus.values());
        return "feature/form";
    }

    @PostMapping("/import")
    public String importFile(@PathVariable Long projectId,
                             @RequestParam("file") MultipartFile file,
                             RedirectAttributes ra) {
        if (file.isEmpty()) {
            ra.addFlashAttribute("importError", "Please select a file to import.");
            return "redirect:/projects/" + projectId + "/features/new";
        }
        try {
            List<FeatureImportService.FeatureRow> rows = featureImportService.parse(file);
            Project project = projectService.findById(projectId);
            int saved = 0;
            for (FeatureImportService.FeatureRow row : rows) {
                Feature f = new Feature();
                f.setTitle(row.getTitle());
                f.setDescription(row.getDescription());
                FeatureStatus status = row.resolveStatus();
                f.setStatus(status);
                f.setProject(project);
                if (status == FeatureStatus.DONE) f.setCompletedAt(LocalDate.now());
                featureRepository.save(f);
                saved++;
            }
            ra.addFlashAttribute("importSuccess",
                "Imported " + saved + " feature" + (saved != 1 ? "s" : "") + " successfully.");
        } catch (Exception e) {
            ra.addFlashAttribute("importError", "Import failed: " + e.getMessage());
        }
        return "redirect:/projects/" + projectId;
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long projectId, @PathVariable Long id, Model model) {
        model.addAttribute("feature", featureRepository.findById(id).orElseThrow());
        model.addAttribute("project", projectService.findById(projectId));
        model.addAttribute("statuses", FeatureStatus.values());
        return "feature/form";
    }

    @PostMapping("/save")
    public String save(@PathVariable Long projectId,
                       @Valid @ModelAttribute Feature feature,
                       BindingResult result,
                       Model model) {
        if (result.hasErrors()) {
            model.addAttribute("project", projectService.findById(projectId));
            model.addAttribute("statuses", FeatureStatus.values());
            return "feature/form";
        }
        Project project = projectService.findById(projectId);
        feature.setProject(project);
        if (feature.getStatus() == FeatureStatus.DONE && feature.getCompletedAt() == null) {
            feature.setCompletedAt(LocalDate.now());
        }
        featureRepository.save(feature);
        return "redirect:/projects/" + projectId;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long projectId, @PathVariable Long id) {
        featureRepository.deleteById(id);
        return "redirect:/projects/" + projectId;
    }

    @PostMapping("/{id}/toggle")
    public String toggleDone(@PathVariable Long projectId, @PathVariable Long id) {
        Feature feature = featureRepository.findById(id).orElseThrow();
        if (feature.getStatus() == FeatureStatus.DONE) {
            feature.setStatus(FeatureStatus.IN_PROGRESS);
            feature.setCompletedAt(null);
        } else {
            feature.setStatus(FeatureStatus.DONE);
            feature.setCompletedAt(LocalDate.now());
        }
        featureRepository.save(feature);
        return "redirect:/projects/" + projectId;
    }
}
