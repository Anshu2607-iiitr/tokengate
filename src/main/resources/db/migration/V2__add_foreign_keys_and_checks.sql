-- V2: data integrity rules jo Hibernate ddl-auto kabhi nahi banata tha

ALTER TABLE usage_records
    ADD CONSTRAINT fk_usage_team FOREIGN KEY (team_id) REFERENCES teams (id),
    ADD CONSTRAINT ck_usage_tokens_non_negative
        CHECK (prompt_tokens >= 0 AND completion_tokens >= 0 AND total_tokens >= 0),
    ADD CONSTRAINT ck_usage_cost_non_negative CHECK (cost_usd >= 0);

ALTER TABLE budget_alerts
    ADD CONSTRAINT fk_alert_team FOREIGN KEY (team_id) REFERENCES teams (id),
    ADD CONSTRAINT ck_alert_threshold_positive CHECK (threshold_percent > 0);

ALTER TABLE teams
    ADD CONSTRAINT ck_team_budget_positive CHECK (daily_budget_usd IS NULL OR daily_budget_usd > 0);

-- Admin alerts list "newest first" dikhati hai
CREATE INDEX idx_alert_created_at ON budget_alerts (created_at DESC);