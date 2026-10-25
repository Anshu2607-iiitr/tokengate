-- V4: har team ka default system prompt (null = koi prompt nahi)
ALTER TABLE teams ADD COLUMN system_prompt VARCHAR(2000);