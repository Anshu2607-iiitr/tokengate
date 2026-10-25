package com.anshu.tokengate.deadletter;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "dead_letters")
public class DeadLetter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "original_topic", length = 100)
    private String originalTopic;

    @Column(name = "original_partition")
    private Integer originalPartition;

    @Column(name = "original_offset")
    private Long originalOffset;

    @Column(name = "exception_class", length = 300)
    private String exceptionClass;

    @Column(name = "exception_message", length = 4000)
    private String exceptionMessage;

    @Column(length = 4000)
    private String payload;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt = Instant.now();

    protected DeadLetter() {
        // JPA ke liye
    }

    public DeadLetter(String originalTopic, Integer originalPartition, Long originalOffset,
                      String exceptionClass, String exceptionMessage, String payload) {
        this.originalTopic = originalTopic;
        this.originalPartition = originalPartition;
        this.originalOffset = originalOffset;
        this.exceptionClass = exceptionClass;
        this.exceptionMessage = exceptionMessage;
        this.payload = payload;
    }

    public Long getId() { return id; }
    public String getOriginalTopic() { return originalTopic; }
    public Integer getOriginalPartition() { return originalPartition; }
    public Long getOriginalOffset() { return originalOffset; }
    public String getExceptionClass() { return exceptionClass; }
    public String getExceptionMessage() { return exceptionMessage; }
    public String getPayload() { return payload; }
    public Instant getReceivedAt() { return receivedAt; }
}