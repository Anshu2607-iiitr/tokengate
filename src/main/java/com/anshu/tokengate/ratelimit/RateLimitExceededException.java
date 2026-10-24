package com.anshu.tokengate.ratelimit;

public class RateLimitExceededException extends RuntimeException {

    private final String teamName;
    private final int limitPerMinute;
    private final long retryAfterSeconds;

    public RateLimitExceededException(String teamName, int limitPerMinute, long retryAfterSeconds) {
        super("Rate limit exceeded for team " + teamName);
        this.teamName = teamName;
        this.limitPerMinute = limitPerMinute;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public String getTeamName() { return teamName; }
    public int getLimitPerMinute() { return limitPerMinute; }
    public long getRetryAfterSeconds() { return retryAfterSeconds; }
}