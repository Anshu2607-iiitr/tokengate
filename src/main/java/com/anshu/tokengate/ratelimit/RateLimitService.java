package com.anshu.tokengate.ratelimit;

import com.anshu.tokengate.team.Team;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class RateLimitService {

    private static final Logger log = LoggerFactory.getLogger(RateLimitService.class);

    private static final long WINDOW_MS = 60_000;

    // Sliding window log: har request ka timestamp ek sorted set me.
    // Poora check ek Lua script me = atomic (do parallel requests dono "9" gin ke pass nahi ho sakti)
    private static final String SLIDING_WINDOW_LUA = """
            local key = KEYS[1]
            local now = tonumber(ARGV[1])
            local window = tonumber(ARGV[2])
            local limit = tonumber(ARGV[3])
            local member = ARGV[4]

            redis.call('ZREMRANGEBYSCORE', key, 0, now - window)
            local count = redis.call('ZCARD', key)

            if count < limit then
              redis.call('ZADD', key, now, member)
              redis.call('PEXPIRE', key, window)
              return {1, limit - count - 1, 0}
            end

            local oldest = redis.call('ZRANGE', key, 0, 0, 'WITHSCORES')
            local retryAfterMs = tonumber(oldest[2]) + window - now
            return {0, 0, retryAfterMs}
            """;

    @SuppressWarnings("rawtypes")
    private final DefaultRedisScript<List> script = new DefaultRedisScript<>(SLIDING_WINDOW_LUA, List.class);

    private final StringRedisTemplate redis;
    private final int requestsPerMinute;

    public RateLimitService(StringRedisTemplate redis,
                            @Value("${tokengate.rate-limit.requests-per-minute:10}") int requestsPerMinute) {
        this.redis = redis;
        this.requestsPerMinute = requestsPerMinute;
    }

    @SuppressWarnings("unchecked")
    public void check(Team team) {
        long now = System.currentTimeMillis();
        String key = "ratelimit:sw:" + team.getId();

        List<Long> result;
        try {
            result = redis.execute(script, List.of(key),
                    String.valueOf(now),
                    String.valueOf(WINDOW_MS),
                    String.valueOf(requestsPerMinute),
                    now + "-" + UUID.randomUUID()); // same millisecond ki do requests bhi alag gini jaayein
        } catch (DataAccessException e) {
            // Fail-open: Redis down ho to bhi gateway chalta rahe
            log.warn("Redis unavailable, skipping rate limit: {}", e.getMessage());
            return;
        }

        if (result == null || result.size() < 3) {
            return;
        }

        boolean allowed = result.get(0) == 1L;
        if (!allowed) {
            long retryAfterSeconds = Math.max(1, (result.get(2) + 999) / 1000); // ms -> sec, upar round
            throw new RateLimitExceededException(team.getName(), requestsPerMinute, retryAfterSeconds);
        }
    }
}