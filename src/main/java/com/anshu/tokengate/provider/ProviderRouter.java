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

    private final List<LlmProvider> providers;

    public ProviderRouter(List<LlmProvider> providers) {
        this.providers = providers;
        log.info("Providers loaded in priority order: {}",
                providers.stream().map(LlmProvider::name).toList());
    }

    public ProviderResult route(ChatRequest request) {
        List<LlmProvider> candidates = candidatesFor(request.model());

        for (int i = 0; i < candidates.size(); i++) {
            LlmProvider provider = candidates.get(i);
            try {
                ChatResponse response = provider.chat(request);
                boolean fallbackUsed = i > 0;
                if (fallbackUsed) {
                    log.warn("FALLBACK model={} served by provider={}", request.model(), provider.name());
                }
                return new ProviderResult(response, provider.name(), fallbackUsed);
            } catch (RestClientException e) {
                log.warn("Provider {} failed for model {}: {}", provider.name(), request.model(), e.getMessage());
            }
        }
        throw new ProviderUnavailableException("All providers failed for model: " + request.model());
    }

    public StreamOutcome routeStream(ChatRequest request, StreamListener listener) {
        List<LlmProvider> candidates = candidatesFor(request.model());

        for (int i = 0; i < candidates.size(); i++) {
            LlmProvider provider = candidates.get(i);
            boolean fallbackUsed = i > 0;
            boolean[] started = {false};
            try {
                LlmProvider.StreamResult result = provider.chatStream(request, data -> {
                    started[0] = true;
                    listener.onChunk(provider.name(), fallbackUsed, data);
                });
                if (fallbackUsed) {
                    log.warn("FALLBACK (stream) model={} served by provider={}", request.model(), provider.name());
                }
                return new StreamOutcome(result, provider.name(), fallbackUsed);
            } catch (RestClientException | UnsupportedOperationException e) {
                if (started[0]) {
                    throw e; // data client tak ja chuka hai, ab provider badal nahi sakte
                }
                log.warn("Provider {} failed to stream model {}: {}", provider.name(), request.model(), e.getMessage());
            }
        }
        throw new ProviderUnavailableException("All providers failed to stream model: " + request.model());
    }

    private List<LlmProvider> candidatesFor(String model) {
        List<LlmProvider> candidates = providers.stream().filter(p -> p.supports(model)).toList();
        if (candidates.isEmpty()) {
            throw new ProviderUnavailableException("No provider supports model: " + model);
        }
        return candidates;
    }

    @FunctionalInterface
    public interface StreamListener {
        void onChunk(String providerName, boolean fallbackUsed, String data);
    }

    public record ProviderResult(ChatResponse response, String providerName, boolean fallbackUsed) {}

    public record StreamOutcome(LlmProvider.StreamResult result, String providerName, boolean fallbackUsed) {}
}