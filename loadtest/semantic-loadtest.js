import http from "k6/http";
import { check, sleep } from "k6";
import { Rate, Counter } from "k6/metrics";

export const options = {
  stages: [
    { duration: "30s", target: 20 },
    { duration: "1m30s", target: 50 },
    { duration: "20s", target: 0 },
  ],
};

const exactHit = new Rate("exact_hit_rate");
const semanticHit = new Rate("semantic_hit_rate");
const anyHit = new Rate("any_cache_hit_rate");
const tokensSaved = new Counter("tokens_saved");

const groups = [
  ["What is India?", "India is what?", "Tell me about India", "Explain India briefly", "what is india"],
  ["How do I reset my password?", "I forgot my password, how do I reset it?", "password reset steps", "How can I change my forgotten password?"],
  ["What is Kafka?", "Explain Apache Kafka", "What is Apache Kafka used for?", "kafka kya hai"],
  ["When is JoSAA round 2?", "JoSAA round 2 date", "When does the second round of JoSAA start?", "JoSAA second round schedule"],
  ["What is an API gateway?", "What does an API gateway do?", "Explain API gateway", "API gateway meaning"],
  ["Is this medicine safe for kids?", "Is this medicine unsafe for kids?"],
];

function header(res, name) {
  const e = Object.entries(res.headers).find(([k]) => k.toLowerCase() === name.toLowerCase());
  return e ? e[1] : "";
}

export default function () {
  const g = groups[Math.floor(Math.random() * groups.length)];
  const q = g[Math.floor(Math.random() * g.length)];

  const res = http.post(
    "http://localhost:8080/v1/chat/completions",
    JSON.stringify({ model: "gpt-4o-mini", messages: [{ role: "user", content: q }] }),
    { headers: { "Content-Type": "application/json", Authorization: `Bearer ${__ENV.TOKENGATE_KEY}` }, timeout: "30s" }
  );
  check(res, { "status 200": (r) => r.status === 200 });

  const provider = header(res, "X-TokenGate-Provider");
  const isExact = provider === "cache";
  const isSemantic = provider === "semantic-cache";
  exactHit.add(isExact);
  semanticHit.add(isSemantic);
  anyHit.add(isExact || isSemantic);
  if ((isExact || isSemantic) && res.status === 200) {
    tokensSaved.add(res.json("usage.total_tokens") || 0);
  }

  sleep(1 + Math.random() * 2);
}