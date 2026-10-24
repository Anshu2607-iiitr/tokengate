package com.anshu.tokengate.usage;

import com.anshu.tokengate.security.ApiKeyAuthFilter;
import com.anshu.tokengate.team.Team;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@RestController
@RequestMapping("/v1/usage")
public class UsageController {

    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    private final UsageRecordRepository usageRecordRepository;

    public UsageController(UsageRecordRepository usageRecordRepository) {
        this.usageRecordRepository = usageRecordRepository;
    }

    @GetMapping("/today")
    public UsageSummary today(@RequestAttribute(ApiKeyAuthFilter.TEAM_ATTRIBUTE) Team team) {
        Instant from = LocalDate.now(ZONE).atStartOfDay(ZONE).toInstant();

        UsageRecordRepository.UsageTotals totals =
                usageRecordRepository.totalsForTeamSince(team.getId(), from);

        List<ModelUsage> byModel = usageRecordRepository.totalsByModelSince(team.getId(), from)
                .stream()
                .map(m -> new ModelUsage(
                        m.getModel(),
                        m.getRequests(),
                        orZero(m.getTotalTokens()),
                        orZero(m.getTotalCost())))
                .toList();

        return new UsageSummary(
                team.getName(),
                "today",
                totals.getRequests(),
                orZero(totals.getTotalTokens()),
                orZero(totals.getTotalCost()),
                byModel);
    }

    private static long orZero(Long value) {
        return value == null ? 0L : value;
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    public record UsageSummary(
            String team,
            String period,
            long requests,
            long totalTokens,
            BigDecimal totalCostUsd,
            List<ModelUsage> byModel
    ) {}

    public record ModelUsage(String model, long requests, long totalTokens, BigDecimal costUsd) {}
}