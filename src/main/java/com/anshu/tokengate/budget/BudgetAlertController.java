package com.anshu.tokengate.budget;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/admin/alerts")
public class BudgetAlertController {

    private final BudgetAlertRepository budgetAlertRepository;

    public BudgetAlertController(BudgetAlertRepository budgetAlertRepository) {
        this.budgetAlertRepository = budgetAlertRepository;
    }

    @GetMapping
    public List<AlertView> list() {
        return budgetAlertRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(a -> new AlertView(a.getTeamName(), a.getAlertDate(), a.getThresholdPercent(),
                        a.getSpentUsd(), a.getBudgetUsd(), a.getCreatedAt()))
                .toList();
    }

    public record AlertView(String team, LocalDate date, int thresholdPercent,
                            BigDecimal spentUsd, BigDecimal budgetUsd, Instant createdAt) {}
}