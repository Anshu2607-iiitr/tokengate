package com.anshu.tokengate.gateway;

import com.anshu.tokengate.dto.ChatRequest;
import com.anshu.tokengate.dto.ChatResponse;
import com.anshu.tokengate.team.Team;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1")
public class GatewayController {

    private final GatewayService gatewayService;

    public GatewayController(GatewayService gatewayService) {
        this.gatewayService = gatewayService;
    }

    @PostMapping("/chat/completions")
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request,
                                             @AuthenticationPrincipal Team team) {
        GatewayService.GatewayResult result = gatewayService.chat(request, team);
        return ResponseEntity.ok()
                .header("X-TokenGate-Cache", result.cacheHit() ? "HIT" : "MISS")
                .header("X-TokenGate-Provider", result.provider())
                .header("X-TokenGate-Fallback", String.valueOf(result.fallbackUsed()))
                .body(result.response());
    }
}