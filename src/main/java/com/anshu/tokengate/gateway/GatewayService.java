package com.anshu.tokengate.gateway;

import com.anshu.tokengate.budget.BudgetService;
import com.anshu.tokengate.cache.ResponseCacheService;
import com.anshu.tokengate.dto.ChatRequest;
import com.anshu.tokengate.dto.ChatResponse;
import com.anshu.tokengate.provider.ProviderRouter;
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

    private final ProviderRouter providerRouter;
    private final UsageEventPublisher usageEventPublisher;
    private final BudgetService budgetService;
    private final RateLimitService rateLimitService;
    private final ResponseCacheService cacheService;

    public GatewayService(ProviderRouter providerRouter,
                          UsageEventPublisher usageEventPublisher,
                          BudgetService budgetService,
                          RateLimitService rateLimitService,
                          ResponseCacheService cacheService) {
        this.providerRouter = providerRouter;
        this.usageEventPublisher = usageEventPublisher;
        this.budgetService = budgetService;
        this.rateLimitService = rateLimitService;
        this.cacheService = cacheService;
    }

    public GatewayResult chat(ChatRequest request, Team team) {
        // 1. Rate limit
        rateLimitService.check(team);

        // 2. Cache
        Optional<ChatResponse> cached = cacheService.get(team.getId(), request);
        if (cached.isPresent()) {
            log.info("CACHE HIT team={} model={} (provider skipped, cost $0)", team.getName(), request.model());
            return new GatewayResult(cached.get(), true, "cache", false);
        }

        // 3. Budget
        budgetService.checkBudget(team);

        // 4. Router: sahi provider chuno, fail ho to fallback
        long start = System.currentTimeMillis();
        ProviderRouter.ProviderResult result = providerRouter.route(request);
        long latencyMs = System.currentTimeMillis() - start;
        ChatResponse response = result.response();

        // 5. Usage event (asli provider ke naam ke saath)
        UsageEvent event = new UsageEvent(
                UUID.randomUUID().toString(),
                team.getId(),
                team.getName(),
                result.providerName(),
                response.model(),
                response.usage().promptTokens(),
                response.usage().completionTokens(),
                response.usage().totalTokens(),
                latencyMs,
                Instant.now()
        );
        usageEventPublisher.publish(event);

        // 6. Cache save
        if (!result.fallbackUsed()) { cacheService.put(team.getId(), request, response); }

        log.info("USAGE team={} provider={} model={} totalTokens={} latencyMs={} fallback={}",
                team.getName(), result.providerName(), response.model(),
                response.usage().totalTokens(), latencyMs, result.fallbackUsed());

        return new GatewayResult(response, false, result.providerName(), result.fallbackUsed());
    }

    public record GatewayResult(ChatResponse response, boolean cacheHit, String provider, boolean fallbackUsed) {}
}