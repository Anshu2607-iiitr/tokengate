package com.anshu.tokengate.team;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

public final class TeamDtos {

    private TeamDtos() {}

    public record CreateTeamRequest(
            @NotBlank
            @Size(max = 50)
            @Pattern(regexp = "^[a-z0-9-]+$", message = "only lowercase letters, numbers and hyphens")
            String name
    ) {}

    public record CreateTeamResponse(Long id, String name, String apiKey, String note) {}

    // null bhejo to limit hat jaayegi
    public record SetBudgetRequest(
            @DecimalMin(value = "0.0", inclusive = false) BigDecimal dailyBudgetUsd
    ) {}

    public record TeamSummary(
            Long id,
            String name,
            String apiKeyPrefix,
            boolean active,
            BigDecimal dailyBudgetUsd,
            Instant createdAt
    ) {}
}