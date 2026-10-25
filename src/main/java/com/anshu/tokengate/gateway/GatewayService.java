package com.anshu.tokengate.gateway;

import com.anshu.tokengate.budget.BudgetService;
import com.anshu.tokengate.cache.ResponseCacheService;
import com.anshu.tokengate.dto.ChatMessage;
import com.anshu.tokengate.dto.ChatRequest;
import com.anshu.tokengate.dto.ChatResponse;
import com.anshu.tokengate.provider.LlmProvider;
import com.anshu.tokengate.provider.ProviderRouter;
import com.anshu.tokengate.ratelimit.RateLimitService;
import com.anshu.tokengate.stream.SseWriter;
import com.anshu.tokengate.stream.StreamChunks;
import com.anshu.tokengate.team.Team;
import com.anshu.tokengate.usage.UsageEvent;
import com.anshu.tokengate.usage.UsageEventPublisher;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
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

    /* ---------------- Normal (non-streaming) ---------------- */

    public GatewayResult chat(ChatRequest request, Team team) {
        rateLimitService.check(team);

        Optional<ChatResponse> cached = cacheService.get(team.getId(), request);
        if (cached.isPresent()) {
            log.info("CACHE HIT team={} model={} (provider skipped, cost $0)", team.getName(), request.model());
            return new GatewayResult(cached.get(), true, "cache", false);
        }

        budgetService.checkBudget(team);

        long start = System.currentTimeMillis();
        ProviderRouter.ProviderResult result = providerRouter.route(request);
        long latencyMs = System.currentTimeMillis() - start;
        ChatResponse response = result.response();

        publishUsage(team, result.providerName(), response, latencyMs);
        if (!result.fallbackUsed()) {
            cacheService.put(team.getId(), request, response);
        }

        log.info("USAGE team={} provider={} model={} totalTokens={} latencyMs={} fallback={}",
                team.getName(), result.providerName(), response.model(),
                response.usage().totalTokens(), latencyMs, result.fallbackUsed());

        return new GatewayResult(response, false, result.providerName(), result.fallbackUsed());
    }

    /* ---------------- Streaming (SSE) ---------------- */

    public void chatStream(ChatRequest request, Team team, HttpServletResponse response) {
        // Pehle wahi checks. Inke errors normal JSON (429 etc.) banenge, kyunki abhi kuch likha nahi gaya
        rateLimitService.check(team);

        Optional<ChatResponse> cached = cacheService.get(team.getId(), request);
        if (cached.isPresent()) {
            ChatResponse c = cached.get();
            SseWriter sse = new SseWriter(response, headers("cache", false, true));
            sse.send(StreamChunks.content(c.id(), c.model(), contentOf(c)));
            sse.send(StreamChunks.finish(c.id(), c.model()));
            sse.send(StreamChunks.usage(c.id(), c.model(), c.usage()));
            sse.done();
            log.info("CACHE HIT (stream) team={} model={} (provider skipped, cost $0)", team.getName(), request.model());
            return;
        }

        budgetService.checkBudget(team);

        long start = System.currentTimeMillis();
        long[] firstChunkAt = {0};
        SseWriter[] writer = {null};

        ProviderRouter.StreamOutcome outcome;
        try {
            outcome = providerRouter.routeStream(request, (providerName, fallbackUsed, data) -> {
                if (writer[0] == null) {
                    // Pehla chunk aaya: ab pata hai kaunsa provider jawab de raha hai, headers set karo
                    writer[0] = new SseWriter(response, headers(providerName, fallbackUsed, false));
                    firstChunkAt[0] = System.currentTimeMillis();
                }
                writer[0].send(data);
            });
        } catch (RuntimeException e) {
            if (writer[0] == null) {
                throw e; // kuch nahi bheja: normal error response (503 etc.)
            }
            log.warn("Stream interrupted team={}: {}", team.getName(), e.getMessage());
            try {
                writer[0].send("{\"error\":\"stream interrupted\"}");
                writer[0].done();
            } catch (RuntimeException ignored) {
                // client ja chuka hai
            }
            return;
        }

        if (writer[0] == null) {
            writer[0] = new SseWriter(response, headers(outcome.providerName(), outcome.fallbackUsed(), false));
            firstChunkAt[0] = System.currentTimeMillis();
        }
        writer[0].done();

        long latencyMs = System.currentTimeMillis() - start;
        long firstTokenMs = firstChunkAt[0] - start;

        // Stream khatam: saare chunks jodke ek normal response banao (usage + cache ke liye)
        LlmProvider.StreamResult result = outcome.result();
        ChatResponse.Usage usage = result.usage() != null ? result.usage() : estimateUsage(request, result.content());
        ChatResponse full = new ChatResponse(
                "stream-" + UUID.randomUUID(),
                result.model(),
                List.of(new ChatResponse.Choice(0, new ChatMessage("assistant", result.content()), "stop")),
                usage);

        publishUsage(team, outcome.providerName(), full, latencyMs);
        if (!outcome.fallbackUsed()) {
            cacheService.put(team.getId(), request, full);
        }

        log.info("USAGE (stream) team={} provider={} model={} totalTokens={} firstTokenMs={} latencyMs={} fallback={}",
                team.getName(), outcome.providerName(), full.model(), usage.totalTokens(),
                firstTokenMs, latencyMs, outcome.fallbackUsed());
    }

    /* ---------------- Helpers ---------------- */

    private void publishUsage(Team team, String providerName, ChatResponse response, long latencyMs) {
        usageEventPublisher.publish(new UsageEvent(
                UUID.randomUUID().toString(),
                team.getId(),
                team.getName(),
                providerName,
                response.model(),
                response.usage().promptTokens(),
                response.usage().completionTokens(),
                response.usage().totalTokens(),
                latencyMs,
                Instant.now()
        ));
    }

    private static Map<String, String> headers(String provider, boolean fallbackUsed, boolean cacheHit) {
        return Map.of(
                "X-TokenGate-Provider", provider,
                "X-TokenGate-Cache", cacheHit ? "HIT" : "MISS",
                "X-TokenGate-Fallback", String.valueOf(fallbackUsed));
    }

    private static String contentOf(ChatResponse response) {
        if (response.choices() == null || response.choices().isEmpty()) {
            return "";
        }
        return response.choices().get(0).message().content();
    }

    // Agar provider ne usage nahi bheja to andaza (4 chars = 1 token)
    private static ChatResponse.Usage estimateUsage(ChatRequest request, String content) {
        int promptChars = request.messages().stream().mapToInt(m -> m.content().length()).sum();
        int prompt = Math.max(1, promptChars / 4);
        int completion = Math.max(1, content.length() / 4);
        return new ChatResponse.Usage(prompt, completion, prompt + completion);
    }

    public record GatewayResult(ChatResponse response, boolean cacheHit, String provider, boolean fallbackUsed) {}
}