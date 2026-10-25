package com.anshu.tokengate.deadletter;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

@Component
public class DeadLetterConsumer {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterConsumer.class);
    private static final int MAX_TEXT = 4000;

    private final DeadLetterRepository repository;

    public DeadLetterConsumer(DeadLetterRepository repository) {
        this.repository = repository;
    }

    @KafkaListener(
            topics = KafkaErrorHandlingConfig.DLT_TOPIC,
            groupId = "dead-letter-store",
            properties = "auto.offset.reset=earliest"
    )
    public void onDeadLetter(ConsumerRecord<String, String> record) {
        // Yahan khud fail nahi hona: warna "DLT ka DLT" ban jaayega
        try {
            DeadLetter deadLetter = new DeadLetter(
                    text(record, KafkaHeaders.DLT_ORIGINAL_TOPIC),
                    intValue(record, KafkaHeaders.DLT_ORIGINAL_PARTITION),
                    longValue(record, KafkaHeaders.DLT_ORIGINAL_OFFSET),
                    truncate(text(record, KafkaHeaders.DLT_EXCEPTION_FQCN)),
                    truncate(text(record, KafkaHeaders.DLT_EXCEPTION_MESSAGE)),
                    truncate(record.value()));
            repository.save(deadLetter);
            log.error("DEAD LETTER stored: topic={} offset={} exception={}",
                    deadLetter.getOriginalTopic(), deadLetter.getOriginalOffset(), deadLetter.getExceptionClass());
        } catch (RuntimeException e) {
            log.error("Could not store dead letter at DLT offset {}: {}", record.offset(), e.getMessage());
        }
    }

    private static String text(ConsumerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    private static Integer intValue(ConsumerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null || header.value().length != 4 ? null : ByteBuffer.wrap(header.value()).getInt();
    }

    private static Long longValue(ConsumerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null || header.value().length != 8 ? null : ByteBuffer.wrap(header.value()).getLong();
    }

    private static String truncate(String value) {
        return value == null || value.length() <= MAX_TEXT ? value : value.substring(0, MAX_TEXT);
    }
}