package com.anshu.tokengate.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record ChatRequest(
        @NotBlank String model,
        @NotEmpty List<@Valid ChatMessage> messages
) {}