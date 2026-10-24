package com.anshu.tokengate.provider;

import com.anshu.tokengate.dto.ChatRequest;
import com.anshu.tokengate.dto.ChatResponse;

public interface LlmProvider {

    String name();

    ChatResponse chat(ChatRequest request);
}