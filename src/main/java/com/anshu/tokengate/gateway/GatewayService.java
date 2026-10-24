package com.anshu.tokengate.gateway;

import com.anshu.tokengate.dto.ChatRequest;
import com.anshu.tokengate.dto.ChatResponse;
import com.anshu.tokengate.provider.LlmProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class GatewayService {

    private static final Logger log = LoggerFactory.getLogger(GatewayService.class);

    private final LlmProvider provider;

    public GatewayService(LlmProvider provider) {
        this.provider = provider;
    }

    public ChatResponse chat(ChatRequest request) {
        long start = System.currentTimeMillis();

        ChatResponse response = provider.chat(request);

        long latencyMs = System.currentTimeMillis() - start;

        log.info("USAGE provider={} model={} promptTokens={} completionTokens={} totalTokens={} latencyMs={}",
                provider.name(),
                response.model(),
                response.usage().promptTokens(),
                response.usage().completionTokens(),
                response.usage().totalTokens(),
                latencyMs);

        return response;
    }
}