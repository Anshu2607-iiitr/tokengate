package com.anshu.tokengate.budget;

import com.anshu.tokengate.team.Team;
import com.anshu.tokengate.usage.UsageRecordRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BudgetServiceTest {

    private final UsageRecordRepository repository = mock(UsageRecordRepository.class);
    private final BudgetService budgetService = new BudgetService(repository);

    private Team team(String dailyBudget) {
        Team team = new Team("test-team", "hash", "tg_test");
        if (dailyBudget != null) {
            team.setDailyBudgetUsd(new BigDecimal(dailyBudget));
        }
        return team;
    }

    private void spentToday(String amount) {
        UsageRecordRepository.UsageTotals totals = mock(UsageRecordRepository.UsageTotals.class);
        when(totals.getTotalCost()).thenReturn(amount == null ? null : new BigDecimal(amount));
        when(repository.totalsForTeamSince(any(), any())).thenReturn(totals);
    }

    @Test
    void teamWithoutBudgetIsNeverBlocked() {
        budgetService.checkBudget(team(null));
        verifyNoInteractions(repository); // DB query bhi nahi honi chahiye
    }

    @Test
    void allowsRequestsUnderBudget() {
        spentToday("0.0002");
        assertThatCode(() -> budgetService.checkBudget(team("0.0003"))).doesNotThrowAnyException();
    }

    @Test
    void blocksWhenBudgetIsReached() {
        spentToday("0.0003");
        assertThatThrownBy(() -> budgetService.checkBudget(team("0.0003")))
                .isInstanceOf(BudgetExceededException.class);
    }

    @Test
    void noUsageYetCountsAsZeroSpend() {
        spentToday(null);
        assertThatCode(() -> budgetService.checkBudget(team("0.0003"))).doesNotThrowAnyException();
    }
}