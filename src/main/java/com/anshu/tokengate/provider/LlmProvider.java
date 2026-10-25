package com.anshu.tokengate.provider;

import com.anshu.tokengate.dto.ChatRequest;
import com.anshu.tokengate.dto.ChatResponse;

import java.util.function.Consumer;

public interface LlmProvider {

    String name();

    boolean supports(String model);

    ChatResponse chat(ChatRequest request);

    // Har chunk aate hi onData ko deta hai; end me poora content + usage return karta hai.
    // default: jo provider streaming support nahi karta, woh error dega (router agla try karega)
    default StreamResult chatStream(ChatRequest request, Consumer<String> onData) {
        throw new UnsupportedOperationException(name() + " does not support streaming");
    }

    record StreamResult(String content, ChatResponse.Usage usage, String model) {}
}