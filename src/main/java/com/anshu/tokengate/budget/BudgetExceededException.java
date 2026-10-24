package com.anshu.tokengate.budget;

import java.math.BigDecimal;

public class BudgetExceededException extends RuntimeException {

    private final String teamName;
    private final BigDecimal spentUsd;
    private final BigDecimal budgetUsd;

    public BudgetExceededException(String teamName, BigDecimal spentUsd, BigDecimal budgetUsd) {
        super("Daily budget exceeded for team " + teamName);
        this.teamName = teamName;
        this.spentUsd = spentUsd;
        this.budgetUsd = budgetUsd;
    }

    public String getTeamName() { return teamName; }
    public BigDecimal getSpentUsd() { return spentUsd; }
    public BigDecimal getBudgetUsd() { return budgetUsd; }
}