package com.anshu.tokengate.mock;

import com.anshu.tokengate.dto.ChatMessage;
import com.anshu.tokengate.dto.ChatRequest;
import com.anshu.tokengate.dto.ChatResponse;
import com.anshu.tokengate.stream.SseWriter;
import com.anshu.tokengate.stream.StreamChunks;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class MockLlmService {

    private final Random random = new Random();

    public ChatResponse complete(ChatRequest request) {
        simulateLatency();
        String reply = replyFor(request);
        int promptTokens = estimateTokens(allInput(request));
        int completionTokens = estimateTokens(reply);

        return new ChatResponse(
                "mock-" + UUID.randomUUID(),
                request.model(),
                List.of(new ChatResponse.Choice(0, new ChatMessage("assistant", reply), "stop")),
                new ChatResponse.Usage(promptTokens, completionTokens, promptTokens + completionTokens)
        );
    }

    // OpenAI-style streaming: ek-ek shabd, phir finish, phir usage, phir [DONE]
    public void stream(ChatRequest request, HttpServletResponse response) {
        String id = "mock-" + UUID.randomUUID();
        String model = request.model();
        String reply = replyFor(request);

        SseWriter sse = new SseWriter(response, Map.of());
        String[] words = reply.split(" ");
        for (int i = 0; i < words.length; i++) {
            sse.send(StreamChunks.content(id, model, (i == 0 ? "" : " ") + words[i]));
            sleep(60);
        }
        sse.send(StreamChunks.finish(id, model));

        int promptTokens = estimateTokens(allInput(request));
        int completionTokens = estimateTokens(reply);
        sse.send(StreamChunks.usage(id, model,
                new ChatResponse.Usage(promptTokens, completionTokens, promptTokens + completionTokens)));
        sse.done();
    }

    private String replyFor(ChatRequest request) {
        List<ChatMessage> messages = request.messages();
        return "Mock reply to: " + messages.get(messages.size() - 1).content();
    }

    private String allInput(ChatRequest request) {
        return request.messages().stream().map(ChatMessage::content).collect(Collectors.joining(" "));
    }

    private int estimateTokens(String text) {
        return Math.max(1, text.length() / 4);
    }

    private void simulateLatency() {
        sleep(200 + random.nextInt(600));
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}