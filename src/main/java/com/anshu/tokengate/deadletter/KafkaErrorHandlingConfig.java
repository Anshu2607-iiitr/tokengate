package com.anshu.tokengate.deadletter;

import com.anshu.tokengate.usage.UsageEventPublisher;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import tools.jackson.core.JacksonException;

@Configuration
public class KafkaErrorHandlingConfig {

    public static final String DLT_TOPIC = UsageEventPublisher.TOPIC + ".DLT";

    // DLT ke partitions original topic jaise, taaki message same partition number pe jaaye
    @Bean
    public NewTopic usageEventsDltTopic() {
        return TopicBuilder.name(DLT_TOPIC).partitions(3).replicas(1).build();
    }

    // Spring Boot ye bean saare @KafkaListener containers pe khud laga deta hai
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate) {
        // Retries khatam: message DLT me (original message + error details headers me)
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, exception) -> new TopicPartition(DLT_TOPIC, record.partition()));

        // Retryable errors: 1s, 2s, 4s ke gap pe 3 retries (exponential backoff)
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(3);
        backOff.setInitialInterval(1_000);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(10_000);

        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);

        // Ye errors retry se theek nahi honge: seedha DLT
        handler.addNotRetryableExceptions(
                JacksonException.class,               // tooti JSON
                DataIntegrityViolationException.class, // e.g. team exist nahi karti (FK)
                IllegalArgumentException.class);
        return handler;
    }
}