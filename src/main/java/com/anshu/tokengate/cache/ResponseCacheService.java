package com.anshu.tokengate.cache;

import com.anshu.tokengate.dto.ChatMessage;
import com.anshu.tokengate.dto.ChatRequest;
import com.anshu.tokengate.dto.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

@Service
public class ResponseCacheService {

    private static final Logger log = LoggerFactory.getLogger(ResponseCacheService.class);

    private final StringRedisTemplate redis;
    private final Duration ttl;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public ResponseCacheService(StringRedisTemplate redis,
                                @Value("${tokengate.cache.ttl-seconds:600}") long ttlSeconds) {
        this.redis = redis;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    public Optional<ChatResponse> get(Long teamId, ChatRequest request) {
        try {
            String json = redis.opsForValue().get(key(teamId, request));
            if (json == null) {
                return Optional.empty();
            }
            return Optional.of(jsonMapper.readValue(json, ChatResponse.class));
        } catch (DataAccessException e) {
            log.warn("Redis unavailable, cache lookup skipped: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public void put(Long teamId, ChatRequest request, ChatResponse response) {
        try {
            redis.opsForValue().set(key(teamId, request), jsonMapper.writeValueAsString(response), ttl);
        } catch (DataAccessException e) {
            log.warn("Redis unavailable, cache save skipped: {}", e.getMessage());
        }
    }

    // Cache key = team + model + NORMALIZED messages.
    // - teamId: ek team ka cache doosri team ko kabhi nahi milega
    // - normalize: "What is India?" aur "what is india" ki same key
    // - stream flag key me nahi: stream ON/OFF dono same cached jawab share karein
    private String key(Long teamId, ChatRequest request) {
        List<ChatMessage> normalized = request.messages().stream()
                .map(m -> new ChatMessage(m.role(), PromptNormalizer.normalize(m.content())))
                .toList();
        String raw = request.model() + "\n" + jsonMapper.writeValueAsString(normalized);
        return "cache:" + teamId + ":" + sha256(raw);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}