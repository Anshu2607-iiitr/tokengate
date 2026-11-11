package com.anshu.tokengate.team;

import com.anshu.tokengate.team.TeamDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/teams")
public class TeamController {

    private final TeamService teamService;

    public TeamController(TeamService teamService) {
        this.teamService = teamService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateTeamResponse create(@Valid @RequestBody CreateTeamRequest request) {
        TeamService.CreatedTeam created = teamService.createTeam(request.name());
        return new CreateTeamResponse(created.team().getId(), created.team().getName(),
                created.apiKey(), "Save this key now. It will not be shown again.");
    }

    @GetMapping
    public List<TeamSummary> list() {
        return teamService.listTeams().stream().map(TeamController::toSummary).toList();
    }

    @PutMapping("/{id}/budget")
    public TeamSummary setBudget(@PathVariable Long id, @Valid @RequestBody SetBudgetRequest request) {
        return toSummary(teamService.setDailyBudget(id, request.dailyBudgetUsd()));
    }

    @PutMapping("/{id}/system-prompt")
    public TeamSummary setSystemPrompt(@PathVariable Long id, @Valid @RequestBody SetSystemPromptRequest request) {
        return toSummary(teamService.setSystemPrompt(id, request.systemPrompt()));
    }

    @PutMapping("/{id}/semantic-cache")
    public TeamSummary setSemanticCache(@PathVariable Long id, @RequestBody SetSemanticCacheRequest request) {
        return toSummary(teamService.setSemanticCacheEnabled(id, request.enabled()));
    }

    private static TeamSummary toSummary(Team t) {
        return new TeamSummary(t.getId(), t.getName(), t.getApiKeyPrefix(), t.isActive(),
                t.getDailyBudgetUsd(), t.getSystemPrompt(), t.isSemanticCacheEnabled(), t.getCreatedAt());
    }
}