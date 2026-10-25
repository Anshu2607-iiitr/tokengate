package com.anshu.tokengate;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
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
class TokenGateIntegrationTest {

    // Day 1 wala timezone bug: test main() nahi chalata, isliye yahan bhi set karna padta hai
    static {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
    }

    private static final String ADMIN_TOKEN = "dev-admin-secret";

    // 4xx/5xx pe exception nahi, taaki status codes khud check kar sakein
    private final RestClient http = RestClient.builder()
            .baseUrl("http://localhost:18080")
            .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> { })
            .build();

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void fullFlow_auth_gateway_kafka_cost_cache_and_security() throws Exception {
        // 1. Admin team banata hai
        Map team = http.post().uri("/admin/teams")
                .header("X-Admin-Token", ADMIN_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "it-team"))
                .retrieve()
                .body(Map.class);
        String apiKey = (String) team.get("apiKey");
        assertThat(apiKey).startsWith("tg_");

        Map<String, Object> chat = Map.of(
                "model", "gpt-4o-mini",
                "messages", List.of(Map.of("role", "user", "content", "Hello from integration test")));

        // 2. Pehli call: provider tak jaati hai
        ResponseEntity<Map> first = http.post().uri("/v1/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(chat)
                .retrieve()
                .toEntity(Map.class);
        assertThat(first.getStatusCode().value()).isEqualTo(200);
        assertThat(first.getHeaders().getFirst("X-TokenGate-Provider")).isEqualTo("mock");
        assertThat(first.getHeaders().getFirst("X-TokenGate-Cache")).isEqualTo("MISS");

        // 3. Kafka -> cost consumer -> Postgres: usage API me dikhna chahiye
        Map usage = waitForUsage(apiKey, 1);
        assertThat(new BigDecimal(usage.get("totalCostUsd").toString())).isPositive();

        // 4. Same request dobara: Redis cache se
        ResponseEntity<Map> second = http.post().uri("/v1/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(chat)
                .retrieve()
                .toEntity(Map.class);
        assertThat(second.getStatusCode().value()).isEqualTo(200);
        assertThat(second.getHeaders().getFirst("X-TokenGate-Cache")).isEqualTo("HIT");

        // 5. Security: bina key ke 401, team key se admin pe 403
        ResponseEntity<Void> noKey = http.get().uri("/v1/usage/today").retrieve().toBodilessEntity();
        assertThat(noKey.getStatusCode().value()).isEqualTo(401);

        ResponseEntity<Void> teamOnAdmin = http.get().uri("/admin/teams")
                .header("Authorization", "Bearer " + apiKey)
                .retrieve()
                .toBodilessEntity();
        assertThat(teamOnAdmin.getStatusCode().value()).isEqualTo(403);
    }

    // Kafka async hai, isliye thodi der tak poll karte hain (max 30 sec)
    @SuppressWarnings("rawtypes")
    private Map waitForUsage(String apiKey, int expectedRequests) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 30_000;
        Map usage = null;
        while (System.currentTimeMillis() < deadline) {
            usage = http.get().uri("/v1/usage/today")
                    .header("Authorization", "Bearer " + apiKey)
                    .retrieve()
                    .body(Map.class);
            if (usage != null && ((Number) usage.get("requests")).intValue() >= expectedRequests) {
                return usage;
            }
            Thread.sleep(500);
        }
        throw new AssertionError("Usage was not recorded via Kafka within 30s. Last response: " + usage);
    }
}