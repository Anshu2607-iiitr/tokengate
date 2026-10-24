package com.anshu.tokengate.gateway;

import com.anshu.tokengate.dto.ChatRequest;
import com.anshu.tokengate.dto.ChatResponse;
import com.anshu.tokengate.security.ApiKeyAuthFilter;
import com.anshu.tokengate.team.Team;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1")
public class GatewayController {

    private final GatewayService gatewayService;

    public GatewayController(GatewayService gatewayService) {
        this.gatewayService = gatewayService;
    }

    @PostMapping("/chat/completions")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request,
                             @RequestAttribute(ApiKeyAuthFilter.TEAM_ATTRIBUTE) Team team) {
        return gatewayService.chat(request, team);
    }
}