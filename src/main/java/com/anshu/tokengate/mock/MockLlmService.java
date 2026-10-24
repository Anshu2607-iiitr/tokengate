package com.anshu.tokengate.mock;

import com.anshu.tokengate.dto.ChatMessage;
import com.anshu.tokengate.dto.ChatRequest;
import com.anshu.tokengate.dto.ChatResponse;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class MockLlmService {

    private final Random random = new Random();

    public ChatResponse complete(ChatRequest request) {
        simulateLatency();

        List<ChatMessage> messages = request.messages();
        String lastMessage = messages.get(messages.size() - 1).content();
        String reply = "Mock reply to: " + lastMessage;

        String allInput = messages.stream()
                .map(ChatMessage::content)
                .collect(Collectors.joining(" "));

        int promptTokens = estimateTokens(allInput);
        int completionTokens = estimateTokens(reply);

        return new ChatResponse(
                "mock-" + UUID.randomUUID(),
                request.model(),
                List.of(new ChatResponse.Choice(0, new ChatMessage("assistant", reply), "stop")),
                new ChatResponse.Usage(promptTokens, completionTokens, promptTokens + completionTokens)
        );
    }

    private int estimateTokens(String text) {
        return Math.max(1, text.length() / 4);
    }

    private void simulateLatency() {
        try {
            Thread.sleep(200 + random.nextInt(600));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}