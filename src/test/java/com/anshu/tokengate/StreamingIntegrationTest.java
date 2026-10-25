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
class StreamingIntegrationTest {

    static {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
    }

    private final RestClient http = RestClient.builder()
            .baseUrl("http://localhost:18080")
            .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> { })
            .build();

    @Test
    @SuppressWarnings("rawtypes")
    void streamsOpenAiStyleChunksAndStillRecordsUsage() throws Exception {
        Map team = http.post().uri("/admin/teams")
                .header("X-Admin-Token", "dev-admin-secret")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "stream-team"))
                .retrieve()
                .body(Map.class);
        String apiKey = (String) team.get("apiKey");

        Map<String, Object> chat = Map.of(
                "model", "gpt-4o-mini",
                "stream", true,
                "messages", List.of(Map.of("role", "user", "content", "Stream this please")));

        ResponseEntity<String> response = http.post().uri("/v1/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(chat)
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(String.valueOf(response.getHeaders().getContentType())).startsWith("text/event-stream");
        assertThat(response.getHeaders().getFirst("X-TokenGate-Provider")).isEqualTo("mock");
        assertThat(response.getBody())
                .contains("chat.completion.chunk")
                .contains("Stream")
                .contains("data: [DONE]");

        // Streaming ke baad bhi usage Kafka se record hona chahiye
        long deadline = System.currentTimeMillis() + 30_000;
        int requests = 0;
        while (System.currentTimeMillis() < deadline && requests < 1) {
            Map usage = http.get().uri("/v1/usage/today")
                    .header("Authorization", "Bearer " + apiKey)
                    .retrieve()
                    .body(Map.class);
            requests = ((Number) usage.get("requests")).intValue();
            if (requests < 1) {
                Thread.sleep(500);
            }
        }
        assertThat(requests).isEqualTo(1);
    }
}