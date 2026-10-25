package com.anshu.tokengate.usage;

import com.anshu.tokengate.metrics.GatewayMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;

@Component
public class UsageCostConsumer {

    private static final Logger log = LoggerFactory.getLogger(UsageCostConsumer.class);

    private final UsageRecordRepository usageRecordRepository;
    private final PricingService pricingService;
    private final GatewayMetrics metrics;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public UsageCostConsumer(UsageRecordRepository usageRecordRepository,
                             PricingService pricingService,
                             GatewayMetrics metrics) {
        this.usageRecordRepository = usageRecordRepository;
        this.pricingService = pricingService;
        this.metrics = metrics;
    }

    @KafkaListener(
            topics = UsageEventPublisher.TOPIC,
            groupId = "cost-calculator",
            properties = "auto.offset.reset=earliest"
    )
    @Transactional
    public void onUsageEvent(String message) {
        UsageEvent event = jsonMapper.readValue(message, UsageEvent.class);

        if (usageRecordRepository.existsByEventId(event.eventId())) {
            log.warn("Duplicate event skipped eventId={}", event.eventId());
            return;
        }

        BigDecimal cost = pricingService.calculateCost(
                event.model(), event.promptTokens(), event.completionTokens());

        usageRecordRepository.save(new UsageRecord(event, cost));
        metrics.cost(event.teamName(), event.model(), cost);

        log.info("COST team={} model={} tokens={} costUsd={}",
                event.teamName(), event.model(), event.totalTokens(), cost.toPlainString());
    }
}