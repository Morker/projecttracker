package com.devtrack.app.logentry;

import com.devtrack.app.project.Project;
import com.devtrack.app.project.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

@Controller
@RequestMapping("/projects/{projectId}/logs")
@RequiredArgsConstructor
public class LogEntryController {

    private final LogEntryRepository logEntryRepository;
    private final ProjectService projectService;

    @GetMapping("/new")
    public String newForm(@PathVariable Long projectId, Model model) {
        model.addAttribute("logEntry", new LogEntry());
        model.addAttribute("project", projectService.findById(projectId));
        return "logentry/form";
    }

    @PostMapping("/save")
    public String save(@PathVariable Long projectId,
                       @Valid @ModelAttribute LogEntry logEntry,
                       BindingResult result,
                       Model model) {
        if (result.hasErrors()) {
            model.addAttribute("project", projectService.findById(projectId));
            return "logentry/form";
        }
        Project project = projectService.findById(projectId);
        logEntry.setProject(project);
        logEntryRepository.save(logEntry);
        return "redirect:/projects/" + projectId;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long projectId, @PathVariable Long id) {
        logEntryRepository.deleteById(id);
        return "redirect:/projects/" + projectId;
    }
}
