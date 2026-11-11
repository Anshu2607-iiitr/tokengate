"""
Embeddings lab: kaunse sawaal "same matlab" wale hain, computer kaise naapta hai?
Chalao: python experiments/embeddings_lab.py
"""
import math

from openai import OpenAI

# Ollama ka OpenAI-compatible API (TokenGate nahi, seedha Ollama)
client = OpenAI(base_url="http://localhost:11434/v1", api_key="ollama")


def embed(text):
    """Sentence -> numbers ki list (vector)."""
    return client.embeddings.create(model="nomic-embed-text", input=text).data[0].embedding


def cosine(a, b):
    """cosine similarity = dot / (len_a * len_b). 1.0 = same direction (same matlab)."""
    dot = sum(x * y for x, y in zip(a, b))      # jode me multiply, sab ka jod
    len_a = math.sqrt(sum(x * x for x in a))    # har number ka square, jod, square root
    len_b = math.sqrt(sum(y * y for y in b))
    return dot / (len_a * len_b)


# (sawaal A, sawaal B, kya inka matlab SAME hai?)
pairs = [
    ("What is India?", "India is what?", True),
    ("heart attack????", "What is a heart attack?", True),
    ("How do I reset my password?", "I forgot my password, please help", True),
    ("JoSAA round 2 date", "When does the second round of JoSAA start?", True),
    ("What is the capital of India?", "What is the capital of Pakistan?", False),  # jaal 1
    ("Is this medicine safe for kids?", "Is this medicine unsafe for kids?", False),  # jaal 2
    ("What is Kafka?", "How do I bake bread?", False),
]

print(f"\n{'SIMILARITY':>10}  {'ASLI ME SAME?':<14} PAIR")
print("-" * 90)
for a, b, same in pairs:
    score = cosine(embed(a), embed(b))
    label = "haan" if same else "NAHI"
    print(f"{score:>10.3f}  {label:<14} {a!r}  vs  {b!r}")
print()

# Vector ki ek jhalak
v = embed("What is India?")
print(f"'What is India?' ka vector: {len(v)} numbers, pehle 5: {[round(x, 3) for x in v[:5]]}\n")