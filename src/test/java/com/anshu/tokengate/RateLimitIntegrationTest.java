package com.anshu.tokengate;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
        properties = {
                "server.port=18080",
                "tokengate.providers.mock.base-url=http://localhost:18080/mock/openai/v1",
                "tokengate.providers.ollama.base-url=http://localhost:1/v1",
                "spring.jpa.show-sql=false"
        })
@Import(TestcontainersConfiguration.class)
class RateLimitIntegrationTest {

    static {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
    }

    private final RestClient http = RestClient.builder()
            .baseUrl("http://localhost:18080")
            .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> { })
            .build();

    @Test
    @SuppressWarnings("rawtypes")
    void slidingWindowAllowsTenThenBlocksWithRetryAfter() {
        Map team = http.post().uri("/admin/teams")
                .header("X-Admin-Token", "dev-admin-secret")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "limit-team"))
                .retrieve()
                .body(Map.class);
        String apiKey = (String) team.get("apiKey");

        Map<String, Object> chat = Map.of(
                "model", "gpt-4o-mini",
                "messages", List.of(Map.of("role", "user", "content", "rate limit test")));

        // Limit = 10/min: pehli 10 pass
        for (int i = 1; i <= 10; i++) {
            ResponseEntity<Void> ok = http.post().uri("/v1/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(chat)
                    .retrieve()
                    .toBodilessEntity();
            assertThat(ok.getStatusCode().value()).as("request %d", i).isEqualTo(200);
        }

        // 11th: block, Retry-After ke saath
        ResponseEntity<Void> blocked = http.post().uri("/v1/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(chat)
                .retrieve()
                .toBodilessEntity();
        assertThat(blocked.getStatusCode().value()).isEqualTo(429);
        long retryAfter = Long.parseLong(blocked.getHeaders().getFirst("Retry-After"));
        assertThat(retryAfter).isBetween(1L, 60L);
    }
}