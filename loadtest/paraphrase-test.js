import http from "k6/http";
import { check, sleep } from "k6";
import { Rate } from "k6/metrics";

// Traffic ka shape: 30s me 100 users, phir 1 min tak 500 users, phir dheere band
export const options = {
  stages: [
    { duration: "30s", target: 100 },
    { duration: "1m", target: 500 },
    { duration: "30s", target: 0 },
  ],
  thresholds: {
    http_req_failed: ["rate<0.01"],    // 1% se kam errors
    http_req_duration: ["p(95)<1500"], // 95% requests 1.5s ke andar
  },
};

const cacheHit = new Rate("cache_hit_rate");

// Har group = ek sawaal, alag shabdon me (jaise asli log poochte hain)
const groups = [
  ["What is India?", "what is india", "India is what?", "Tell me about India", "Explain India briefly"],
  ["How do I reset my password?", "how to reset password", "I forgot my password, what do I do?", "Password reset steps?"],
  ["What is Kafka?", "what is kafka", "Explain Apache Kafka", "Kafka kya hai?"],
  ["When is JoSAA round 2?", "JoSAA round 2 date", "when does second round of josaa start", "JoSAA 2nd round schedule?"],
  ["What is an API gateway?", "what does an api gateway do", "Explain API gateway", "API gateway kya hota hai?"],
];

export default function () {
  const group = groups[Math.floor(Math.random() * groups.length)];
  const question = group[Math.floor(Math.random() * group.length)];

  const res = http.post(
    "http://localhost:8080/v1/chat/completions",
    JSON.stringify({ model: "gpt-4o-mini", messages: [{ role: "user", content: question }] }),
    { headers: { "Content-Type": "application/json", Authorization: `Bearer ${__ENV.TOKENGATE_KEY}` } }
  );

  check(res, { "status is 200": (r) => r.status === 200 });
  const cacheHeader = Object.entries(res.headers).find(([k]) => k.toLowerCase() === "x-tokengate-cache");
  cacheHit.add(cacheHeader && cacheHeader[1] === "HIT");

  sleep(1 + Math.random() * 2); // "think time": asli insaan jaisa wait
}