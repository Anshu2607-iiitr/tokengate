# Semantic cache

TokenGate answers repeated questions from cache, even when they are phrased differently.

## Layers

1. **Exact cache (Redis)**: the cache key uses normalized text (lowercase, collapsed whitespace, trailing `?.!` removed), so `What is India?` and `WHAT IS INDIA!!` share one entry. The `stream` flag is not part of the key.
2. **Semantic cache (pgvector)**: if the exact cache misses, the question is embedded (`nomic-embed-text` via Ollama, 768 dimensions) and the nearest stored question for the same team, model and system prompt is found with an HNSW index. It is served only if cosine similarity is at least **0.85** *and* the guards pass.
3. **Provider**: otherwise the request goes to the model, and the answer is stored in both caches.

## Safety rules

- **Opt-in per team** (`PUT /admin/teams/{id}/semantic-cache`), off by default.
- **Single-turn only**: multi-turn chats depend on history, so they are never served semantically.
- **Scoped** by team, model and a hash of the system prompt.
- **Guards** reject matches with a negation mismatch (`safe` vs `unsafe`, `not`, `can't`) or different numbers (`round 2` vs `round 3`).
- **Fail-open**: if the embedding service or vector search fails, the request simply continues to the provider.
- Entries expire after 24 hours.

## Why guards are needed

In a small experiment with `nomic-embed-text`:

| Pair | Similarity | Same meaning? |
|---|---|---|
| What is India? / India is what? | 0.958 | yes |
| heart attack???? / What is a heart attack? | 0.887 | yes |
| What is the capital of India? / ...of Pakistan? | 0.762 | **no** |
| Is this medicine safe for kids? / ...unsafe for kids? | **0.961** | **no** |

The unsafe pair scored *higher* than every true paraphrase, so no similarity threshold alone can separate them. The negation guard blocks it.

## Load test (k6)

~2,000 requests per run, up to 50 virtual users, mock provider, embeddings on local CPU. Team A had semantic cache off, team B on. Full table: [`loadtest/results.md`](../loadtest/results.md).

- 100% success, p95 latency under 10 ms in both runs.
- The semantic layer recovered roughly 40% of the requests the exact cache missed.
- The guard blocked every `safe` vs `unsafe` attempt under load (3 of 3).
- Limitation: the synthetic traffic reused about 25 phrasings, so the normalized exact cache already served ~99% of requests. Realistic traffic (many unique human phrasings) is the next evaluation.

## Not done yet

- Precision/recall on a large labeled dataset (Quora Question Pairs script: `experiments/quora_eval.py`).
- A scheduled job to delete expired rows from `semantic_cache`.