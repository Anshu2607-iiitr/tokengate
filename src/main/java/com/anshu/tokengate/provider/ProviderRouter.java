package com.anshu.tokengate.provider;

import com.anshu.tokengate.dto.ChatRequest;
import com.anshu.tokengate.dto.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

import java.util.List;

@Component
public class ProviderRouter {

    private static final Logger log = LoggerFactory.getLogger(ProviderRouter.class);

    private final List<LlmProvider> providers; // @Order ke hisaab se sorted

    public ProviderRouter(List<LlmProvider> providers) {
        this.providers = providers;
        log.info("Providers loaded in priority order: {}",
                providers.stream().map(LlmProvider::name).toList());
    }

    public ProviderResult route(ChatRequest request) {
        String model = request.model();

        List<LlmProvider> candidates = providers.stream()
                .filter(p -> p.supports(model))
                .toList();

        if (candidates.isEmpty()) {
            throw new ProviderUnavailableException("No provider supports model: " + model);
        }

        for (int i = 0; i < candidates.size(); i++) {
            LlmProvider provider = candidates.get(i);
            try {
                ChatResponse response = provider.chat(request);
                boolean fallbackUsed = i > 0;
                if (fallbackUsed) {
                    log.warn("FALLBACK model={} served by provider={}", model, provider.name());
                }
                return new ProviderResult(response, provider.name(), fallbackUsed);
            } catch (RestClientException e) {
                log.warn("Provider {} failed for model {}: {}", provider.name(), model, e.getMessage());
            }
        }

        throw new ProviderUnavailableException("All providers failed for model: " + model);
    }

    public record ProviderResult(ChatResponse response, String providerName, boolean fallbackUsed) {}
}