package com.devtrack.app.project;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

@Controller
@RequestMapping("/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;
    private final MarkdownService markdownService;

    @GetMapping
    public String list(@RequestParam(required = false) String status,
                       @RequestParam(required = false) String q,
                       Model model) {
        if (q != null && !q.isBlank()) {
            model.addAttribute("projects", projectService.search(q));
            model.addAttribute("q", q);
        } else if (status != null && !status.isBlank()) {
            model.addAttribute("projects", projectService.findByStatus(ProjectStatus.valueOf(status)));
            model.addAttribute("activeStatus", status);
        } else {
            model.addAttribute("projects", projectService.findAll());
        }
        model.addAttribute("statuses", ProjectStatus.values());
        return "project/list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("project", projectService.findById(id));
        return "project/detail";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("project", new Project());
        model.addAttribute("statuses", ProjectStatus.values());
        return "project/form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("project", projectService.findById(id));
        model.addAttribute("statuses", ProjectStatus.values());
        return "project/form";
    }

    @PostMapping("/save")
    public String save(@Valid @ModelAttribute Project project,
                       BindingResult result,
                       @AuthenticationPrincipal UserDetails user,
                       Model model) {
        if (result.hasErrors()) {
            model.addAttribute("statuses", ProjectStatus.values());
            return "project/form";
        }
        Project saved = projectService.save(project, user.getUsername());
        return "redirect:/projects/" + saved.getId();
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id) {
        projectService.delete(id);
        return "redirect:/projects";
    }

    @GetMapping("/{id}/docs")
    public String docs(@PathVariable Long id, Model model) {
        Project project = projectService.findById(id);
        model.addAttribute("project", project);
        model.addAttribute("docsHtml", markdownService.toHtml(project.getDocumentation()));
        return "project/docs";
    }

    @GetMapping("/{id}/docs/edit")
    public String docsEdit(@PathVariable Long id, Model model) {
        model.addAttribute("project", projectService.findById(id));
        return "project/docs-form";
    }

    @PostMapping("/{id}/docs/save")
    public String docsSave(@PathVariable Long id,
                           @RequestParam(required = false) String documentation,
                           @AuthenticationPrincipal UserDetails user) {
        Project project = projectService.findById(id);
        project.setDocumentation(documentation);
        projectService.save(project, user.getUsername());
        return "redirect:/projects/" + id + "/docs";
    }
}
