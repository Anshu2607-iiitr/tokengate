package com.anshu.tokengate.team;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "teams")
public class Team {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String name;

    @Column(name = "api_key_hash", nullable = false, unique = true, length = 64)
    private String apiKeyHash;

    @Column(name = "api_key_prefix", nullable = false, length = 12)
    private String apiKeyPrefix;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Team() {
        // JPA ke liye zaroori
    }

    public Team(String name, String apiKeyHash, String apiKeyPrefix) {
        this.name = name;
        this.apiKeyHash = apiKeyHash;
        this.apiKeyPrefix = apiKeyPrefix;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getApiKeyHash() { return apiKeyHash; }
    public String getApiKeyPrefix() { return apiKeyPrefix; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
}