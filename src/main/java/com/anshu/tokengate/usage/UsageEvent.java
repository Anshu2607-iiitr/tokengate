package com.anshu.tokengate.usage;

import java.time.Instant;

public record UsageEvent(
        String eventId,
        Long teamId,
        String teamName,
        String provider,
        String model,
        int promptTokens,
        int completionTokens,
        int totalTokens,
        long latencyMs,
        Instant timestamp
) {}