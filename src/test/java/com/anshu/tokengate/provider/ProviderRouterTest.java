package com.anshu.tokengate.provider;

import com.anshu.tokengate.dto.ChatMessage;
import com.anshu.tokengate.dto.ChatRequest;
import com.anshu.tokengate.dto.ChatResponse;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProviderRouterTest {

    private static ChatRequest request(String model) {
        return new ChatRequest(model, List.of(new ChatMessage("user", "hi")));
    }

    private static ChatResponse okResponse(String model) {
        return new ChatResponse("id-1", model, List.of(), new ChatResponse.Usage(1, 1, 2));
    }

    // Test ke liye nakli provider: healthy=false ho to "connection refused" jaisa error
    private record FakeProvider(String name, Set<String> models, boolean healthy) implements LlmProvider {
        @Override
        public boolean supports(String model) {
            return models.contains("*") || models.contains(model);
        }

        @Override
        public ChatResponse chat(ChatRequest request) {
            if (!healthy) {
                throw new ResourceAccessException("Connection refused");
            }
            return okResponse(request.model());
        }
    }

    private static ProviderRouter router(LlmProvider... providers) {
        return new ProviderRouter(List.of(providers));
    }

    @Test
    void usesPrimaryProviderWhenHealthy() {
        ProviderRouter router = router(
                new FakeProvider("ollama", Set.of("qwen2.5:1.5b"), true),
                new FakeProvider("mock", Set.of("*"), true));

        ProviderRouter.ProviderResult result = router.route(request("qwen2.5:1.5b"));

        assertThat(result.providerName()).isEqualTo("ollama");
        assertThat(result.fallbackUsed()).isFalse();
    }

    @Test
    void fallsBackWhenPrimaryProviderIsDown() {
        ProviderRouter router = router(
                new FakeProvider("ollama", Set.of("qwen2.5:1.5b"), false),
                new FakeProvider("mock", Set.of("*"), true));

        ProviderRouter.ProviderResult result = router.route(request("qwen2.5:1.5b"));

        assertThat(result.providerName()).isEqualTo("mock");
        assertThat(result.fallbackUsed()).isTrue();
    }

    @Test
    void skipsProvidersThatDoNotSupportTheModel() {
        ProviderRouter router = router(
                new FakeProvider("ollama", Set.of("qwen2.5:1.5b"), true),
                new FakeProvider("mock", Set.of("*"), true));

        ProviderRouter.ProviderResult result = router.route(request("gpt-4o"));

        assertThat(result.providerName()).isEqualTo("mock");
        assertThat(result.fallbackUsed()).isFalse();
    }

    @Test
    void failsClearlyWhenAllProvidersAreDown() {
        ProviderRouter router = router(
                new FakeProvider("ollama", Set.of("qwen2.5:1.5b"), false),
                new FakeProvider("mock", Set.of("*"), false));

        assertThatThrownBy(() -> router.route(request("qwen2.5:1.5b")))
                .isInstanceOf(ProviderUnavailableException.class);
    }
}