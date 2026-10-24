package com.anshu.tokengate.team;

import com.anshu.tokengate.team.TeamDtos.CreateTeamRequest;
import com.anshu.tokengate.team.TeamDtos.CreateTeamResponse;
import com.anshu.tokengate.team.TeamDtos.TeamSummary;
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
        return new CreateTeamResponse(
                created.team().getId(),
                created.team().getName(),
                created.apiKey(),
                "Save this key now. It will not be shown again."
        );
    }

    @GetMapping
    public List<TeamSummary> list() {
        return teamService.listTeams().stream()
                .map(t -> new TeamSummary(t.getId(), t.getName(), t.getApiKeyPrefix(), t.isActive(), t.getCreatedAt()))
                .toList();
    }
}