"""
Semantic cache evaluation on REAL human-written question pairs (Quora Question Pairs, GLUE QQP).
Chalao: python experiments/quora_eval.py
"""
import math
import re

from datasets import load_dataset
from openai import OpenAI

N = 1000  # kitne jode test karne hain
client = OpenAI(base_url="http://localhost:11434/v1", api_key="ollama")

# ---------- Data ----------
print("Dataset load ho raha hai...")
ds = load_dataset("nyu-mll/glue", "qqp", split="validation").shuffle(seed=42).select(range(N))
q1, q2, labels = list(ds["question1"]), list(ds["question2"]), list(ds["label"])
positives = sum(labels)
print(f"{N} jode, jinme {positives} asli duplicates ({positives / N:.0%})")

# ---------- Embeddings (batch me, tez) ----------
def embed_all(texts, batch=32):
    out = []
    for i in range(0, len(texts), batch):
        resp = client.embeddings.create(model="nomic-embed-text", input=texts[i:i + batch])
        out.extend(d.embedding for d in resp.data)
        print(f"  embedded {min(i + batch, len(texts))}/{len(texts)}", end="\r")
    print()
    return out

def cosine(a, b):
    dot = sum(x * y for x, y in zip(a, b))
    return dot / (math.sqrt(sum(x * x for x in a)) * math.sqrt(sum(y * y for y in b)))

# ---------- Guards (Java SemanticGuards ka Python version) ----------
NEGATIONS = {"not", "no", "never", "nor", "without", "cannot", "dont", "doesnt", "didnt", "isnt",
             "arent", "wasnt", "werent", "cant", "wont", "shouldnt", "wouldnt", "couldnt"}
PREFIXES = ["un", "in", "im", "il", "ir", "dis", "non"]

def normalize(t):
    t = re.sub(r"\s+", " ", t.lower()).strip()
    return re.sub(r"[?.!]+$", "", t).strip()

def words(t):
    t = normalize(t).replace("'", "").replace("\u2019", "")
    return [w for w in re.split(r"[^a-z0-9]+", t) if w]

def prefix_antonym(a, b):
    bs = set(b)
    return any(w not in bs and w.startswith(p) and len(w) - len(p) >= 3 and w[len(p):] in bs
               for w in a for p in PREFIXES)

def safe_match(x, y):
    wx, wy = words(x), words(y)
    if sum(w in NEGATIONS for w in wx) % 2 != sum(w in NEGATIONS for w in wy) % 2:
        return False
    if prefix_antonym(wx, wy) or prefix_antonym(wy, wx):
        return False
    nx, ny = set(re.findall(r"\d+(?:\.\d+)?", x)), set(re.findall(r"\d+(?:\.\d+)?", y))
    return not (nx and ny and nx != ny)

# ---------- Run ----------
print("Embeddings ban rahe hain (CPU pe 1-3 min)...")
e1, e2 = embed_all(q1), embed_all(q2)
sims = [cosine(a, b) for a, b in zip(e1, e2)]
guard_ok = [safe_match(a, b) for a, b in zip(q1, q2)]

def stats(threshold, use_guards):
    hits = [i for i in range(N) if sims[i] >= threshold and (guard_ok[i] or not use_guards)]
    tp = sum(labels[i] for i in hits)
    fp = len(hits) - tp
    precision = tp / len(hits) if hits else 1.0
    recall = tp / positives if positives else 0.0
    return len(hits), fp, precision, recall

print(f"\n{'THRESH':>6} | {'HITS':>5} {'GALAT':>5} {'PRECISION':>9} {'RECALL':>7} | +GUARDS: {'HITS':>5} {'GALAT':>5} {'PRECISION':>9} {'RECALL':>7}")
print("-" * 95)
for th in [0.80, 0.82, 0.84, 0.85, 0.86, 0.88, 0.90, 0.92, 0.94, 0.96]:
    h, fp, p, r = stats(th, False)
    hg, fpg, pg, rg = stats(th, True)
    print(f"{th:>6.2f} | {h:>5} {fp:>5} {p:>9.1%} {r:>7.1%} |          {hg:>5} {fpg:>5} {pg:>9.1%} {rg:>7.1%}")

# ---------- Galtiyan khud dekho ----------
TH = 0.85  # TokenGate ka current threshold
wrong = sorted((i for i in range(N) if sims[i] >= TH and guard_ok[i] and labels[i] == 0),
               key=lambda i: -sims[i])[:8]
print(f"\nThreshold {TH} + guards pe GALAT HITS (label kehta hai alag, cache kehta same):")
for i in wrong:
    print(f"  {sims[i]:.3f}  {q1[i]!r}\n         {q2[i]!r}\n")