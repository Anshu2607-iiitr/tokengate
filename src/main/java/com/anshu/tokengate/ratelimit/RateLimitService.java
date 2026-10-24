package com.anshu.tokengate.ratelimit;

import com.anshu.tokengate.team.Team;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
public class RateLimitService {

    private static final Logger log = LoggerFactory.getLogger(RateLimitService.class);

    private final StringRedisTemplate redis;
    private final int requestsPerMinute;

    public RateLimitService(StringRedisTemplate redis,
                            @Value("${tokengate.rate-limit.requests-per-minute:10}") int requestsPerMinute) {
        this.redis = redis;
        this.requestsPerMinute = requestsPerMinute;
    }

    // Fixed window: har minute ka alag counter
    public void check(Team team) {
        long nowSeconds = Instant.now().getEpochSecond();
        long currentMinute = nowSeconds / 60;
        String key = "ratelimit:" + team.getId() + ":" + currentMinute;

        Long count;
        try {
            count = redis.opsForValue().increment(key);
            if (count != null && count == 1) {
                redis.expire(key, Duration.ofSeconds(70)); // purane counters apne aap delete
            }
        } catch (DataAccessException e) {
            // Fail-open: Redis down ho to bhi gateway chalta rahe
            log.warn("Redis unavailable, skipping rate limit: {}", e.getMessage());
            return;
        }

        if (count != null && count > requestsPerMinute) {
            long retryAfter = 60 - (nowSeconds % 60);
            throw new RateLimitExceededException(team.getName(), requestsPerMinute, retryAfter);
        }
    }
}