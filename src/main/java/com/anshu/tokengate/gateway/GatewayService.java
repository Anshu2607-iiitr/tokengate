package com.anshu.tokengate.gateway;

import com.anshu.tokengate.budget.BudgetService;
import com.anshu.tokengate.cache.ResponseCacheService;
import com.anshu.tokengate.dto.ChatRequest;
import com.anshu.tokengate.dto.ChatResponse;
import com.anshu.tokengate.provider.LlmProvider;
import com.anshu.tokengate.ratelimit.RateLimitService;
import com.anshu.tokengate.team.Team;
import com.anshu.tokengate.usage.UsageEvent;
import com.anshu.tokengate.usage.UsageEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class GatewayService {

    private static final Logger log = LoggerFactory.getLogger(GatewayService.class);

    private final LlmProvider provider;
    private final UsageEventPublisher usageEventPublisher;
    private final BudgetService budgetService;
    private final RateLimitService rateLimitService;
    private final ResponseCacheService cacheService;

    public GatewayService(LlmProvider provider,
                          UsageEventPublisher usageEventPublisher,
                          BudgetService budgetService,
                          RateLimitService rateLimitService,
                          ResponseCacheService cacheService) {
        this.provider = provider;
        this.usageEventPublisher = usageEventPublisher;
        this.budgetService = budgetService;
        this.rateLimitService = rateLimitService;
        this.cacheService = cacheService;
    }

    public GatewayResult chat(ChatRequest request, Team team) {
        // 1. Rate limit (sabse sasta check, sabse pehle)
        rateLimitService.check(team);

        // 2. Cache: mila to provider skip, cost $0
        Optional<ChatResponse> cached = cacheService.get(team.getId(), request);
        if (cached.isPresent()) {
            log.info("CACHE HIT team={} model={} (provider skipped, cost $0)", team.getName(), request.model());
            return new GatewayResult(cached.get(), true);
        }

        // 3. Budget
        budgetService.checkBudget(team);

        // 4. Provider call
        long start = System.currentTimeMillis();
        ChatResponse response = provider.chat(request);
        long latencyMs = System.currentTimeMillis() - start;

        // 5. Usage event Kafka me
        UsageEvent event = new UsageEvent(
                UUID.randomUUID().toString(),
                team.getId(),
                team.getName(),
                provider.name(),
                response.model(),
                response.usage().promptTokens(),
                response.usage().completionTokens(),
                response.usage().totalTokens(),
                latencyMs,
                Instant.now()
        );
        usageEventPublisher.publish(event);

        // 6. Agli baar ke liye cache me save
        cacheService.put(team.getId(), request, response);

        log.info("USAGE team={} model={} totalTokens={} latencyMs={} cache=MISS",
                team.getName(), response.model(), response.usage().totalTokens(), latencyMs);

        return new GatewayResult(response, false);
    }

    public record GatewayResult(ChatResponse response, boolean cacheHit) {}
}