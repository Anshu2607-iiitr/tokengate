package com.anshu.tokengate.stream;

import com.anshu.tokengate.dto.ChatResponse;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// OpenAI-style "chat.completion.chunk" JSON banane ke helpers
public final class StreamChunks {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private StreamChunks() {}

    public static String content(String id, String model, String content) {
        Map<String, Object> delta = new LinkedHashMap<>();
        delta.put("content", content);
        return chunk(id, model, List.of(choice(delta, null)), null);
    }

    public static String finish(String id, String model) {
        return chunk(id, model, List.of(choice(new LinkedHashMap<>(), "stop")), null);
    }

    public static String usage(String id, String model, ChatResponse.Usage usage) {
        Map<String, Object> u = new LinkedHashMap<>();
        u.put("prompt_tokens", usage.promptTokens());
        u.put("completion_tokens", usage.completionTokens());
        u.put("total_tokens", usage.totalTokens());
        return chunk(id, model, List.of(), u);
    }

    private static Map<String, Object> choice(Map<String, Object> delta, String finishReason) {
        Map<String, Object> choice = new LinkedHashMap<>();
        choice.put("index", 0);
        choice.put("delta", delta);
        choice.put("finish_reason", finishReason);
        return choice;
    }

    private static String chunk(String id, String model, List<?> choices, Map<String, Object> usage) {
        Map<String, Object> chunk = new LinkedHashMap<>();
        chunk.put("id", id);
        chunk.put("object", "chat.completion.chunk");
        chunk.put("model", model);
        chunk.put("choices", choices);
        if (usage != null) {
            chunk.put("usage", usage);
        }
        return JSON.writeValueAsString(chunk);
    }
}