package com.anshu.tokengate.mock;

import com.anshu.tokengate.dto.ChatRequest;
import com.anshu.tokengate.dto.ChatResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/mock/openai/v1")
public class MockLlmController {

    private final MockLlmService mockLlmService;

    public MockLlmController(MockLlmService mockLlmService) {
        this.mockLlmService = mockLlmService;
    }

    @PostMapping("/chat/completions")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        return mockLlmService.complete(request);
    }
}