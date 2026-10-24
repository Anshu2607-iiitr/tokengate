package com.anshu.tokengate.budget;

import com.anshu.tokengate.team.Team;
import com.anshu.tokengate.team.TeamRepository;
import com.anshu.tokengate.usage.PricingService;
import com.anshu.tokengate.usage.UsageEvent;
import com.anshu.tokengate.usage.UsageEventPublisher;
import com.anshu.tokengate.usage.UsageRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Component
public class BudgetAlertConsumer {

    private static final Logger log = LoggerFactory.getLogger(BudgetAlertConsumer.class);
    private static final List<Integer> THRESHOLDS = List.of(80, 100);

    private final TeamRepository teamRepository;
    private final UsageRecordRepository usageRecordRepository;
    private final BudgetAlertRepository budgetAlertRepository;
    private final PricingService pricingService;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public BudgetAlertConsumer(TeamRepository teamRepository,
                               UsageRecordRepository usageRecordRepository,
                               BudgetAlertRepository budgetAlertRepository,
                               PricingService pricingService) {
        this.teamRepository = teamRepository;
        this.usageRecordRepository = usageRecordRepository;
        this.budgetAlertRepository = budgetAlertRepository;
        this.pricingService = pricingService;
    }

    @KafkaListener(
            topics = UsageEventPublisher.TOPIC,
            groupId = "budget-alerts",
            properties = "auto.offset.reset=earliest"
    )
    @Transactional
    public void onUsageEvent(String message) {
        UsageEvent event = jsonMapper.readValue(message, UsageEvent.class);

        Team team = teamRepository.findById(event.teamId()).orElse(null);
        if (team == null || team.getDailyBudgetUsd() == null) {
            return; // budget set nahi hai, alert ki zarurat nahi
        }
        BigDecimal budget = team.getDailyBudgetUsd();

        LocalDate day = event.timestamp().atZone(BudgetService.ZONE).toLocalDate();
        Instant from = day.atStartOfDay(BudgetService.ZONE).toInstant();

        // Is event ko chhodke DB ka total + is event ka cost = double counting se bachav
        BigDecimal others = usageRecordRepository.costForTeamSinceExcluding(team.getId(), from, event.eventId());
        BigDecimal thisCost = pricingService.calculateCost(
                event.model(), event.promptTokens(), event.completionTokens());
        BigDecimal spent = (others == null ? BigDecimal.ZERO : others).add(thisCost);

        int percent = spent.multiply(BigDecimal.valueOf(100))
                .divide(budget, 0, RoundingMode.DOWN)
                .intValue();

        for (int threshold : THRESHOLDS) {
            if (percent >= threshold
                    && !budgetAlertRepository.existsByTeamIdAndAlertDateAndThresholdPercent(team.getId(), day, threshold)) {
                budgetAlertRepository.save(new BudgetAlert(team.getId(), team.getName(), day, threshold, spent, budget));
                log.warn("ALERT team={} reached {}% of daily budget (spent ${} of ${})",
                        team.getName(), threshold, spent.toPlainString(), budget.toPlainString());
            }
        }
    }
}