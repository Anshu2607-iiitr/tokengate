package com.anshu.tokengate.metrics;

import com.anshu.tokengate.deadletter.DeadLetterRepository;
import com.anshu.tokengate.team.Team;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;

// Saare custom metrics ek jagah. Prometheus naam: dots -> underscores, counters me "_total"
@Component
public class GatewayMetrics {

    private final MeterRegistry registry;

    public GatewayMetrics(MeterRegistry registry, DeadLetterRepository deadLetters) {
        this.registry = registry;
        // Gauge: scrape ke waqt DB se current count
        Gauge.builder("tokengate.kafka.dead.letters", deadLetters, repo -> repo.count())
                .description("Events parked in the dead letter store")
                .register(registry);
    }

    public void request(Team team, String provider, String model, boolean cacheHit, boolean stream, boolean fallback) {
        Counter.builder("tokengate.gateway.requests")
                .description("Gateway chat requests that were served")
                .tag("team", team.getName())
                .tag("provider", provider)
                .tag("model", model)
                .tag("cache", cacheHit ? "hit" : "miss")
                .tag("stream", String.valueOf(stream))
                .tag("fallback", String.valueOf(fallback))
                .register(registry)
                .increment();
    }

    public void tokens(Team team, String model, int tokens) {
        Counter.builder("tokengate.gateway.tokens")
                .description("Tokens used per team and model")
                .tag("team", team.getName())
                .tag("model", model)
                .register(registry)
                .increment(tokens);
    }

    public void latency(String provider, boolean stream, long millis) {
        Timer.builder("tokengate.gateway.latency")
                .description("Time spent waiting for the provider")
                .tag("provider", provider)
                .tag("stream", String.valueOf(stream))
                .publishPercentileHistogram() // p50/p95 Prometheus me nikal sakein
                .register(registry)
                .record(Duration.ofMillis(millis));
    }

    public void firstToken(String provider, long millis) {
        Timer.builder("tokengate.gateway.first.token")
                .description("Streaming: time until the first chunk")
                .tag("provider", provider)
                .publishPercentileHistogram()
                .register(registry)
                .record(Duration.ofMillis(millis));
    }

    public void rejected(String reason) {
        Counter.builder("tokengate.gateway.rejections")
                .description("Requests rejected before or at the provider")
                .tag("reason", reason)
                .register(registry)
                .increment();
    }

    public void cost(String team, String model, BigDecimal usd) {
        Counter.builder("tokengate.cost.usd")
                .description("Cost computed by the Kafka cost consumer")
                .tag("team", team)
                .tag("model", model)
                .register(registry)
                .increment(usd.doubleValue());
    }
}