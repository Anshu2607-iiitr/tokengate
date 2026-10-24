package com.anshu.tokengate.budget;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface BudgetAlertRepository extends JpaRepository<BudgetAlert, Long> {

    boolean existsByTeamIdAndAlertDateAndThresholdPercent(Long teamId, LocalDate alertDate, int thresholdPercent);

    List<BudgetAlert> findAllByOrderByCreatedAtDesc();
}