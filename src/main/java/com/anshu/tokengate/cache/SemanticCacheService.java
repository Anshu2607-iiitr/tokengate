package com.anshu.tokengate.cache;

import com.anshu.tokengate.dto.ChatMessage;
import com.anshu.tokengate.dto.ChatRequest;
import com.anshu.tokengate.dto.ChatResponse;
import com.anshu.tokengate.team.Team;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class SemanticCacheService {

    private static final Logger log = LoggerFactory.getLogger(SemanticCacheService.class);

    private final JdbcTemplate jdbc;
    private final EmbeddingClient embeddings;
    private final double threshold;
    private final int ttlHours;
    private final JsonMapper json = JsonMapper.builder().build();

    public SemanticCacheService(JdbcTemplate jdbc,
                                EmbeddingClient embeddings,
                                @Value("${tokengate.semantic-cache.threshold:0.85}") double threshold,
                                @Value("${tokengate.semantic-cache.ttl-hours:24}") int ttlHours) {
        this.jdbc = jdbc;
        this.embeddings = embeddings;
        this.threshold = threshold;
        this.ttlHours = ttlHours;
    }

    public record Hit(ChatResponse response, double similarity, String matchedPrompt) {}

    // Lookup ka result: hit mila ya nahi, aur baad me SAVE karne ke liye embedding (dobara calculate na karna pade)
    public record Lookup(boolean eligible, String userText, String contextHash, String vector, Optional<Hit> hit) {
        static Lookup skip() {
            return new Lookup(false, null, null, null, Optional.empty());
        }
    }

    public Lookup lookup(Team team, ChatRequest request) {
        if (!team.isSemanticCacheEnabled()) {
            return Lookup.skip();
        }
        String userText = singleUserMessage(request);
        if (userText == null) {
            return Lookup.skip(); // multi-turn chat: "uski population?" history pe depend karta hai, risky
        }
        try {
            String vector = toVectorLiteral(embeddings.embed(userText));
            String contextHash = contextHash(request);

            List<Hit> nearest = jdbc.query("""
                    SELECT prompt_text, response_json, 1 - (embedding <=> CAST(? AS vector)) AS similarity
                    FROM semantic_cache
                    WHERE team_id = ? AND model = ? AND context_hash = ? AND expires_at > now()
                    ORDER BY embedding <=> CAST(? AS vector)
                    LIMIT 1
                    """,
                    (rs, i) -> new Hit(
                            json.readValue(rs.getString("response_json"), ChatResponse.class),
                            rs.getDouble("similarity"),
                            rs.getString("prompt_text")),
                    vector, team.getId(), request.model(), contextHash, vector);

            Optional<Hit> best = nearest.stream().findFirst();
            Optional<Hit> accepted = best
                    .filter(h -> h.similarity() >= threshold)                               // Lesson 2
                    .filter(h -> SemanticGuards.isSafeMatch(userText, h.matchedPrompt()));  // Lesson 3

            best.ifPresent(h -> log.info("SEMANTIC check team={} similarity={} accepted={} new='{}' matched='{}'",
                    team.getName(), String.format("%.3f", h.similarity()), accepted.isPresent(), userText, h.matchedPrompt()));

            return new Lookup(true, userText, contextHash, vector, accepted);
        } catch (RuntimeException e) {
            // Fail-open: embedding/DB problem ho to semantic cache skip, gateway chalta rahe
            log.warn("Semantic cache unavailable, skipped: {}", e.getMessage());
            return Lookup.skip();
        }
    }

    public void store(Team team, ChatRequest request, Lookup lookup, ChatResponse response) {
        if (!lookup.eligible()) {
            return;
        }
        try {
            jdbc.update("""
                    INSERT INTO semantic_cache
                        (team_id, model, context_hash, prompt_text, embedding, response_json, created_at, expires_at)
                    VALUES (?, ?, ?, ?, CAST(? AS vector), ?, now(), now() + make_interval(hours => ?))
                    """,
                    team.getId(), request.model(), lookup.contextHash(), lookup.userText(),
                    lookup.vector(), json.writeValueAsString(response), ttlHours);
        } catch (RuntimeException e) {
            log.warn("Semantic cache save skipped: {}", e.getMessage());
        }
    }

    // Sirf single-turn: (system prompt optional) + exactly EK user message
    private static String singleUserMessage(ChatRequest request) {
        List<ChatMessage> nonSystem = request.messages().stream()
                .filter(m -> !"system".equalsIgnoreCase(m.role()))
                .toList();
        if (nonSystem.size() != 1 || !"user".equalsIgnoreCase(nonSystem.get(0).role())) {
            return null;
        }
        String text = nonSystem.get(0).content();
        return text == null || text.isBlank() || text.length() > 2000 ? null : text;
    }

    // System prompt badla = alag context = purane jawab match nahi honge
    private static String contextHash(ChatRequest request) {
        String system = request.messages().stream()
                .filter(m -> "system".equalsIgnoreCase(m.role()))
                .map(ChatMessage::content)
                .collect(Collectors.joining("\n"));
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(system.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // pgvector text format: [0.1,0.2,...]
    private static String toVectorLiteral(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vector[i]);
        }
        return sb.append(']').toString();
    }
}