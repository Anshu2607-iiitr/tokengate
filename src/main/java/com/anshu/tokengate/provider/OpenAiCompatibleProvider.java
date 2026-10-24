package com.anshu.tokengate.provider;

import com.anshu.tokengate.dto.ChatRequest;
import com.anshu.tokengate.dto.ChatResponse;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Set;

public class OpenAiCompatibleProvider implements LlmProvider {

    private final String name;
    private final Set<String> models;
    private final RestClient restClient;

    public OpenAiCompatibleProvider(String name, String baseUrl, String apiKey,
                                    Set<String> models, Duration readTimeout) {
        this.name = name;
        this.models = models;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(2)); // band server pe jaldi fail
        requestFactory.setReadTimeout(readTimeout);              // AI ko sochne ka time

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public boolean supports(String model) {
        return models.contains("*") || models.contains(model);
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        return restClient.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(ChatResponse.class);
    }
}