package com.anshu.tokengate.stream;

import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

// Server-Sent Events: har chunk "data: ...\n\n" format me, aur turant flush
public class SseWriter {

    private final OutputStream out;

    public SseWriter(HttpServletResponse response, Map<String, String> headers) {
        response.setStatus(200);
        response.setContentType("text/event-stream");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no"); // proxies ko buffering mat karne do
        headers.forEach(response::setHeader);
        try {
            this.out = response.getOutputStream();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void send(String data) {
        write("data: " + data + "\n\n");
    }

    public void done() {
        write("data: [DONE]\n\n");
    }

    private void write(String text) {
        try {
            out.write(text.getBytes(StandardCharsets.UTF_8));
            out.flush(); // flush ke bina chunk buffer me pada rahega
        } catch (IOException e) {
            throw new UncheckedIOException("Client disconnected", e);
        }
    }
}