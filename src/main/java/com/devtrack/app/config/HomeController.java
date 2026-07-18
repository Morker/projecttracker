package com.devtrack.app.config;

import com.devtrack.app.logentry.LogEntryRepository;
import com.devtrack.app.project.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private final ProjectService projectService;
    private final LogEntryRepository logEntryRepository;

    @GetMapping("/")
    public String dashboard(Model model) {
        model.addAttribute("stats", projectService.getDashboardStats());
        model.addAttribute("recentProjects", projectService.findAll().stream().limit(5).toList());
        model.addAttribute("recentLogs", logEntryRepository.findTop5ByOrderByCreatedAtDesc());
        return "dashboard";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }
}
