package com.anshu.tokengate.budget;

import com.anshu.tokengate.team.Team;
import com.anshu.tokengate.usage.UsageRecordRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

@Service
public class BudgetService {

    public static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    private final UsageRecordRepository usageRecordRepository;

    public BudgetService(UsageRecordRepository usageRecordRepository) {
        this.usageRecordRepository = usageRecordRepository;
    }

    // Provider ko call karne se PEHLE chalta hai
    public void checkBudget(Team team) {
        BigDecimal budget = team.getDailyBudgetUsd();
        if (budget == null) {
            return; // no limit
        }
        BigDecimal spent = spentToday(team.getId());
        if (spent.compareTo(budget) >= 0) {
            throw new BudgetExceededException(team.getName(), spent, budget);
        }
    }

    public BigDecimal spentToday(Long teamId) {
        BigDecimal total = usageRecordRepository.totalsForTeamSince(teamId, startOfToday()).getTotalCost();
        return total == null ? BigDecimal.ZERO : total;
    }

    public static Instant startOfToday() {
        return LocalDate.now(ZONE).atStartOfDay(ZONE).toInstant();
    }
}