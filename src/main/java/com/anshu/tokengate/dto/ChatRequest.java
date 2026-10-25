package com.anshu.tokengate.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatRequest(
        @NotBlank String model,
        @NotEmpty List<@Valid ChatMessage> messages,
        Boolean stream
) {
    // Purana code (aur tests) bina stream ke bhi chalein
    public ChatRequest(String model, List<ChatMessage> messages) {
        this(model, messages, null);
    }

    // "isStream" naam nahi rakha: Jackson use JSON property samajh ke "stream" se takra deta
    public boolean streamRequested() {
        return Boolean.TRUE.equals(stream);
    }
}