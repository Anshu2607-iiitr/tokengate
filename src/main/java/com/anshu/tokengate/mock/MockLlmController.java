package com.anshu.tokengate.mock;

import com.anshu.tokengate.dto.ChatRequest;
import com.anshu.tokengate.dto.ChatResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/mock/openai/v1")
public class MockLlmController {

    private final MockLlmService mockLlmService;

    public MockLlmController(MockLlmService mockLlmService) {
        this.mockLlmService = mockLlmService;
    }

    @PostMapping("/chat/completions")
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request,
                                             HttpServletResponse servletResponse) {
        if (request.streamRequested()) {
            mockLlmService.stream(request, servletResponse);
            return null; // response hum khud likh chuke hain
        }
        return ResponseEntity.ok(mockLlmService.complete(request));
    }
}