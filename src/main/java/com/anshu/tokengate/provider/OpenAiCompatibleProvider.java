package com.anshu.tokengate.provider;

import com.anshu.tokengate.dto.ChatRequest;
import com.anshu.tokengate.dto.ChatResponse;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.json.JsonMapper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public class OpenAiCompatibleProvider implements LlmProvider {

    private final String name;
    private final Set<String> models;
    private final RestClient restClient;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public OpenAiCompatibleProvider(String name, String baseUrl, String apiKey,
                                    Set<String> models, Duration readTimeout) {
        this.name = name;
        this.models = models;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(2));
        requestFactory.setReadTimeout(readTimeout);

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

    @Override
    @SuppressWarnings("unchecked")
    public StreamResult chatStream(ChatRequest request, Consumer<String> onData) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", request.model());
        body.put("messages", request.messages());
        body.put("stream", true);
        body.put("stream_options", Map.of("include_usage", true)); // aakhri chunk me exact tokens

        return restClient.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .body(body)
                .exchange((req, res) -> {
                    if (res.getStatusCode().isError()) {
                        throw new RestClientException(name + " returned " + res.getStatusCode());
                    }
                    StringBuilder content = new StringBuilder();
                    ChatResponse.Usage usage = null;
                    String model = request.model();

                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(res.getBody(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (!line.startsWith("data:")) {
                                continue;
                            }
                            String data = line.substring(5).trim();
                            if (data.isEmpty()) {
                                continue;
                            }
                            if ("[DONE]".equals(data)) {
                                break;
                            }

                            Map<String, Object> chunk = jsonMapper.readValue(data, Map.class);
                            if (chunk.get("choices") instanceof List<?> choices && !choices.isEmpty()
                                    && choices.get(0) instanceof Map<?, ?> choice
                                    && choice.get("delta") instanceof Map<?, ?> delta
                                    && delta.get("content") instanceof String piece) {
                                content.append(piece);
                            }
                            if (chunk.get("usage") instanceof Map<?, ?> u) {
                                usage = new ChatResponse.Usage(
                                        toInt(u.get("prompt_tokens")),
                                        toInt(u.get("completion_tokens")),
                                        toInt(u.get("total_tokens")));
                            }
                            if (chunk.get("model") instanceof String m) {
                                model = m;
                            }

                            onData.accept(data); // chunk turant client tak
                        }
                    }
                    return new StreamResult(content.toString(), usage, model);
                });
    }

    private static int toInt(Object value) {
        return value instanceof Number n ? n.intValue() : 0;
    }
}