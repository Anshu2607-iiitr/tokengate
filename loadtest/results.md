# Semantic cache load test

Date: 2026-11-11 17:13
Traffic: k6, up to 50 virtual users, paraphrased questions, mock provider, embeddings via local Ollama (CPU)

| Metric | A: semantic OFF | B: semantic ON |
|---|---|---|
| Any cache hit rate | 98.8% | 99.3% |
| exact cache hits | 98.8% | 98.8% |
| semantic cache hits | 0.0% | 0.5% |
| Tokens saved | 30,417 | 31,293 |
| Total requests | 1,981 | 1,963 |
| Latency median | 5 ms | 4 ms |
| Latency p95 | 8 ms | 7 ms |
| Success (status 200) | 100.0% | 100.0% |

Semantic HITs (logs): 21. Guard blocks (safe vs unsafe): 3.
