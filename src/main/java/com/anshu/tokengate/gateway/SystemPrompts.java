package com.anshu.tokengate.gateway;

import com.anshu.tokengate.dto.ChatMessage;
import com.anshu.tokengate.dto.ChatRequest;
import com.anshu.tokengate.team.Team;

import java.util.ArrayList;
import java.util.List;

// Team ka default system prompt request ke shuru me jodta hai
final class SystemPrompts {

    private SystemPrompts() {}

    static ChatRequest apply(ChatRequest request, Team team) {
        String prompt = team.getSystemPrompt();
        if (prompt == null || prompt.isBlank()) {
            return request;
        }
        // Client ne khud system message bheja hai to woh zyada important hai
        boolean hasSystemMessage = request.messages().stream()
                .anyMatch(m -> "system".equalsIgnoreCase(m.role()));
        if (hasSystemMessage) {
            return request;
        }
        List<ChatMessage> messages = new ArrayList<>(request.messages().size() + 1);
        messages.add(new ChatMessage("system", prompt));
        messages.addAll(request.messages());
        return new ChatRequest(request.model(), messages, request.stream());
    }
}