package com.anshu.tokengate;

import com.anshu.tokengate.usage.UsageEventPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatusCode;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;

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
class DeadLetterIntegrationTest {

    static {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
    }

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    private final RestClient http = RestClient.builder()
            .baseUrl("http://localhost:18080")
            .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> { })
            .build();

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void poisonPillsGoToDeadLetterTopicInsteadOfBeingLost() throws Exception {
        String brokenMarker = "broken-json-" + UUID.randomUUID();
        String ghostEventId = UUID.randomUUID().toString();

        // 1. Tooti JSON
        kafkaTemplate.send(UsageEventPublisher.TOPIC, "1", "{this is not json " + brokenMarker).get();

        // 2. Valid JSON, par team 999999 exist nahi karti: foreign key reject karega
        String ghostEvent = """
                {"eventId":"%s","teamId":999999,"teamName":"ghost","provider":"mock","model":"gpt-4o-mini",
                 "promptTokens":1,"completionTokens":1,"totalTokens":2,"latencyMs":1,"timestamp":"%s"}
                """.formatted(ghostEventId, Instant.now());
        kafkaTemplate.send(UsageEventPublisher.TOPIC, "999999", ghostEvent).get();

        // Dono dead_letters me pahuchne chahiye
        long deadline = System.currentTimeMillis() + 60_000;
        boolean brokenFound = false;
        boolean ghostFound = false;
        while (System.currentTimeMillis() < deadline && !(brokenFound && ghostFound)) {
            List<Map> letters = http.get().uri("/admin/dead-letters")
                    .header("X-Admin-Token", "dev-admin-secret")
                    .retrieve()
                    .body(List.class);
            for (Map letter : letters) {
                String payload = String.valueOf(letter.get("payload"));
                if (payload.contains(brokenMarker)) {
                    brokenFound = true;
                    assertThat(letter.get("exceptionClass")).isNotNull();
                }
                if (payload.contains(ghostEventId)) {
                    ghostFound = true;
                }
            }
            if (!(brokenFound && ghostFound)) {
                Thread.sleep(500);
            }
        }

        assertThat(brokenFound).as("broken JSON reached the DLT").isTrue();
        assertThat(ghostFound).as("event for missing team reached the DLT").isTrue();
    }
}