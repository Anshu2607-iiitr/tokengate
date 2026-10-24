package com.anshu.tokengate.usage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class UsageEventPublisher {

    public static final String TOPIC = "llm-usage-events";

    private static final Logger log = LoggerFactory.getLogger(UsageEventPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public UsageEventPublisher(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(UsageEvent event) {
        String json = jsonMapper.writeValueAsString(event);

        // Key = teamId: ek team ke saare events ek hi partition me, order me
        kafkaTemplate.send(TOPIC, String.valueOf(event.teamId()), json)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish usage event {}", event.eventId(), ex);
                    } else {
                        log.info("EVENT published eventId={} partition={} offset={}",
                                event.eventId(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}