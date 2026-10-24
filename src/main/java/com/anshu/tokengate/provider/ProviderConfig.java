package com.anshu.tokengate.provider;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Configuration
public class ProviderConfig {

    @Bean
    @Order(1)
    public LlmProvider ollamaProvider(
            @Value("${tokengate.providers.ollama.base-url:http://localhost:11434/v1}") String baseUrl,
            @Value("${tokengate.providers.ollama.api-key:ollama}") String apiKey,
            @Value("${tokengate.providers.ollama.models:qwen2.5:1.5b}") String models) {
        return new OpenAiCompatibleProvider("ollama", baseUrl, apiKey, toSet(models), Duration.ofSeconds(60));
    }

    @Bean
    @Order(2)
    public LlmProvider mockProvider(
            @Value("${tokengate.providers.mock.base-url:http://localhost:8080/mock/openai/v1}") String baseUrl,
            @Value("${tokengate.providers.mock.api-key:mock-key}") String apiKey) {
        return new OpenAiCompatibleProvider("mock", baseUrl, apiKey, Set.of("*"), Duration.ofSeconds(10));
    }

    private static Set<String> toSet(String commaSeparated) {
        return Arrays.stream(commaSeparated.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }
}