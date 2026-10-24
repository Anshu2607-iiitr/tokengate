package com.anshu.tokengate.budget;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "budget_alerts",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_alert_team_day_threshold",
                columnNames = {"team_id", "alert_date", "threshold_percent"}))
public class BudgetAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "team_id", nullable = false)
    private Long teamId;

    @Column(name = "team_name", nullable = false, length = 50)
    private String teamName;

    @Column(name = "alert_date", nullable = false)
    private LocalDate alertDate;

    @Column(name = "threshold_percent", nullable = false)
    private int thresholdPercent;

    @Column(name = "spent_usd", nullable = false, precision = 18, scale = 10)
    private BigDecimal spentUsd;

    @Column(name = "budget_usd", nullable = false, precision = 12, scale = 6)
    private BigDecimal budgetUsd;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected BudgetAlert() {
        // JPA ke liye
    }

    public BudgetAlert(Long teamId, String teamName, LocalDate alertDate, int thresholdPercent,
                       BigDecimal spentUsd, BigDecimal budgetUsd) {
        this.teamId = teamId;
        this.teamName = teamName;
        this.alertDate = alertDate;
        this.thresholdPercent = thresholdPercent;
        this.spentUsd = spentUsd;
        this.budgetUsd = budgetUsd;
    }

    public Long getId() { return id; }
    public Long getTeamId() { return teamId; }
    public String getTeamName() { return teamName; }
    public LocalDate getAlertDate() { return alertDate; }
    public int getThresholdPercent() { return thresholdPercent; }
    public BigDecimal getSpentUsd() { return spentUsd; }
    public BigDecimal getBudgetUsd() { return budgetUsd; }
    public Instant getCreatedAt() { return createdAt; }
}