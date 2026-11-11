package com.anshu.tokengate.cache;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

// Sentence -> vector (768 numbers). Python lab ke embed() ka Java version.
@Component
public class EmbeddingClient {

    private final RestClient restClient;
    private final String model;

    public EmbeddingClient(@Value("${tokengate.providers.ollama.base-url:http://localhost:11434/v1}") String baseUrl,
                           @Value("${tokengate.semantic-cache.embedding-model:nomic-embed-text}") String model) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader("Authorization", "Bearer ollama")
                .build();
        this.model = model;
    }

    @SuppressWarnings("unchecked")
    public float[] embed(String text) {
        Map<String, Object> response = restClient.post()
                .uri("/embeddings")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("model", model, "input", text))
                .retrieve()
                .body(Map.class);

        List<?> data = (List<?>) response.get("data");
        List<?> numbers = (List<?>) ((Map<?, ?>) data.get(0)).get("embedding");
        float[] vector = new float[numbers.size()];
        for (int i = 0; i < vector.length; i++) {
            vector[i] = ((Number) numbers.get(i)).floatValue();
        }
        return vector;
    }
}