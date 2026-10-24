package com.anshu.tokengate.usage;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "usage_records",
        indexes = @Index(name = "idx_usage_team_time", columnList = "team_id, created_at"))
public class UsageRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, length = 36)
    private String eventId;

    @Column(name = "team_id", nullable = false)
    private Long teamId;

    @Column(name = "team_name", nullable = false, length = 50)
    private String teamName;

    @Column(nullable = false, length = 30)
    private String provider;

    @Column(nullable = false, length = 100)
    private String model;

    @Column(name = "prompt_tokens", nullable = false)
    private int promptTokens;

    @Column(name = "completion_tokens", nullable = false)
    private int completionTokens;

    @Column(name = "total_tokens", nullable = false)
    private int totalTokens;

    @Column(name = "cost_usd", nullable = false, precision = 18, scale = 10)
    private BigDecimal costUsd;

    @Column(name = "latency_ms", nullable = false)
    private long latencyMs;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected UsageRecord() {
        // JPA ke liye
    }

    public UsageRecord(UsageEvent event, BigDecimal costUsd) {
        this.eventId = event.eventId();
        this.teamId = event.teamId();
        this.teamName = event.teamName();
        this.provider = event.provider();
        this.model = event.model();
        this.promptTokens = event.promptTokens();
        this.completionTokens = event.completionTokens();
        this.totalTokens = event.totalTokens();
        this.latencyMs = event.latencyMs();
        this.createdAt = event.timestamp();
        this.costUsd = costUsd;
    }

    public Long getId() { return id; }
    public String getEventId() { return eventId; }
    public Long getTeamId() { return teamId; }
    public String getTeamName() { return teamName; }
    public String getProvider() { return provider; }
    public String getModel() { return model; }
    public int getPromptTokens() { return promptTokens; }
    public int getCompletionTokens() { return completionTokens; }
    public int getTotalTokens() { return totalTokens; }
    public BigDecimal getCostUsd() { return costUsd; }
    public long getLatencyMs() { return latencyMs; }
    public Instant getCreatedAt() { return createdAt; }
}