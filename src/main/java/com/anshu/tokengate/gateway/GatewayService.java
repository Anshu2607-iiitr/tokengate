package com.anshu.tokengate.gateway;

import com.anshu.tokengate.dto.ChatRequest;
import com.anshu.tokengate.dto.ChatResponse;
import com.anshu.tokengate.provider.LlmProvider;
import com.anshu.tokengate.team.Team;
import com.anshu.tokengate.usage.UsageEvent;
import com.anshu.tokengate.usage.UsageEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class GatewayService {

    private static final Logger log = LoggerFactory.getLogger(GatewayService.class);

    private final LlmProvider provider;
    private final UsageEventPublisher usageEventPublisher;

    public GatewayService(LlmProvider provider, UsageEventPublisher usageEventPublisher) {
        this.provider = provider;
        this.usageEventPublisher = usageEventPublisher;
    }

    public ChatResponse chat(ChatRequest request, Team team) {
        long start = System.currentTimeMillis();

        ChatResponse response = provider.chat(request);

        long latencyMs = System.currentTimeMillis() - start;

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

        log.info("USAGE team={} model={} totalTokens={} latencyMs={}",
                team.getName(), response.model(), response.usage().totalTokens(), latencyMs);

        return response;
    }
}