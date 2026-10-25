"""
TokenGate ke saath ek asli chatbot, OpenAI ki OFFICIAL library se.
Koi TokenGate-specific code nahi: sirf base_url aur api_key badle hain.

Chalao:
  $env:TOKENGATE_KEY = "tg_..."      (team key)
  python examples/chat_cli.py
"""
import os

from openai import OpenAI

client = OpenAI(
    base_url=os.getenv("TOKENGATE_URL", "http://localhost:8080/v1"),  # OpenAI ki jagah TokenGate
    api_key=os.environ["TOKENGATE_KEY"],                              # TokenGate team key
)
MODEL = os.getenv("TOKENGATE_MODEL", "qwen2.5:1.5b")

history = []  # poori baatcheet: isi se bot pichli baatein yaad rakhta hai
print(f"TokenGate chatbot ({MODEL}). Band karne ke liye 'exit' likho.\n")

while True:
    user = input("You: ").strip()
    if user.lower() in ("exit", "quit"):
        break
    if not user:
        continue

    history.append({"role": "user", "content": user})
    print("AI:  ", end="", flush=True)

    reply = ""
    try:
        stream = client.chat.completions.create(model=MODEL, messages=history, stream=True)
        for chunk in stream:
            if chunk.choices and chunk.choices[0].delta and chunk.choices[0].delta.content:
                piece = chunk.choices[0].delta.content
                reply += piece
                print(piece, end="", flush=True)  # live typing
        print("\n")
        history.append({"role": "assistant", "content": reply})
    except Exception as e:
        print(f"\n[TokenGate ne mana kiya] {e}\n")
        history.pop()  # fail hua message history se hata do