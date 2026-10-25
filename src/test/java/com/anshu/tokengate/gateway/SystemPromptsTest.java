package com.anshu.tokengate.gateway;

import com.anshu.tokengate.dto.ChatMessage;
import com.anshu.tokengate.dto.ChatRequest;
import com.anshu.tokengate.team.Team;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SystemPromptsTest {

    private Team team(String prompt) {
        Team team = new Team("t", "hash", "tg_test");
        team.setSystemPrompt(prompt);
        return team;
    }

    private ChatRequest userOnly() {
        return new ChatRequest("m", List.of(new ChatMessage("user", "yoo")));
    }

    @Test
    void noPromptLeavesRequestUnchanged() {
        ChatRequest request = userOnly();
        assertThat(SystemPrompts.apply(request, team(null))).isSameAs(request);
    }

    @Test
    void prependsTeamPromptAsSystemMessage() {
        ChatRequest result = SystemPrompts.apply(userOnly(), team("Always reply in English."));
        assertThat(result.messages()).hasSize(2);
        assertThat(result.messages().get(0).role()).isEqualTo("system");
        assertThat(result.messages().get(0).content()).isEqualTo("Always reply in English.");
        assertThat(result.messages().get(1).content()).isEqualTo("yoo");
    }

    @Test
    void respectsClientsOwnSystemMessage() {
        ChatRequest withSystem = new ChatRequest("m", List.of(
                new ChatMessage("system", "Reply like a pirate."),
                new ChatMessage("user", "yoo")));
        assertThat(SystemPrompts.apply(withSystem, team("Always reply in English."))).isSameAs(withSystem);
    }
}