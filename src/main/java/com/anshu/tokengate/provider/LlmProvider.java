package com.anshu.tokengate.provider;

import com.anshu.tokengate.dto.ChatRequest;
import com.anshu.tokengate.dto.ChatResponse;

public interface LlmProvider {

    String name();

    // Kya ye provider is model ko chala sakta hai?
    boolean supports(String model);

    ChatResponse chat(ChatRequest request);
}