package com.anshu.tokengate.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatMessage(
        @NotBlank String role,
        @NotBlank String content
) {}