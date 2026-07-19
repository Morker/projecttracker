package com.devtrack.app.ai;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class AiController {

    private final AiAssistantService aiAssistantService;

    public AiController(AiAssistantService aiAssistantService) {
        this.aiAssistantService = aiAssistantService;
    }

    @PostMapping("/assistant")
    public AssistantResponse chat(@RequestBody AssistantRequest request) {
        String reply = aiAssistantService.chat(request.getMessage());
        return new AssistantResponse(reply);
    }
}
