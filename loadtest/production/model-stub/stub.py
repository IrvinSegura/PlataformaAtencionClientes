"""Stands in for the Claude Messages API and Voyage AI's embeddings in the production-like load test.

Answers stream as Claude streams them (message_start, text deltas, message_stop) with Claude's timing: a wait before
the first token, then a steady rate. Embeddings hash words and character trigrams, so related texts land close
together, after a short wait. The backend runs its real Anthropic and Voyage clients against it, so the run measures
the platform while answers take seconds, as they do in production, without paying for any.
"""

import hashlib
import json
import math
import os
import random
import re
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

FIRST_TOKEN_MS = [int(v) for v in os.environ.get("FIRST_TOKEN_MS", "800-2000").split("-")]
TOKENS_PER_SECOND = float(os.environ.get("TOKENS_PER_SECOND", "60"))
EMBED_MS = [int(v) for v in os.environ.get("EMBED_MS", "100-300").split("-")]
DIMENSIONS = 1024
ANSWER = (
    "Con gusto te ayudo. Según nuestras políticas, el envío nacional tarda de 3 a 5 días hábiles y es gratis en "
    "compras mayores a 999 pesos; si necesitas devolver un producto tienes 30 días desde que lo recibes, sin usar y "
    "con su empaque. ¿Hay algo más en lo que te pueda ayudar?"
)

counts = {"answers": 0, "embeddings": 0, "open": 0, "peak_open": 0}
lock = threading.Lock()


def pause(bounds):
    time.sleep(random.uniform(bounds[0], bounds[-1]) / 1000)


def embedding(text):
    vector = [0.0] * DIMENSIONS
    words = re.findall(r"\w+", text.lower())
    features = words + [word[i:i + 3] for word in words for i in range(max(1, len(word) - 2))]
    for feature in features:
        digest = hashlib.blake2b(feature.encode(), digest_size=8).digest()
        index = int.from_bytes(digest[:4], "little") % DIMENSIONS
        vector[index] += 1.0 if digest[4] & 1 else -1.0
    norm = math.sqrt(sum(v * v for v in vector)) or 1.0
    return [v / norm for v in vector]


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.0"

    def log_message(self, *args):
        pass

    def do_GET(self):
        with lock:
            body = json.dumps(counts).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(body)

    def do_POST(self):
        request = json.loads(self.rfile.read(int(self.headers.get("Content-Length", 0))) or b"{}")
        if self.path == "/v1/embeddings":
            self.embeddings(request)
        elif self.path == "/v1/messages":
            self.messages(request)
        else:
            self.send_error(404)

    def embeddings(self, request):
        pause(EMBED_MS)
        data = [{"object": "embedding", "index": i, "embedding": embedding(text)}
                for i, text in enumerate(request.get("input", []))]
        body = json.dumps({"object": "list", "data": data, "model": request.get("model")}).encode()
        with lock:
            counts["embeddings"] += 1
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def messages(self, request):
        with lock:
            counts["open"] += 1
            counts["peak_open"] = max(counts["peak_open"], counts["open"])
        try:
            self.send_response(200)
            self.send_header("Content-Type", "text/event-stream")
            self.end_headers()
            prompt = json.dumps(request.get("messages", []))
            self.event("message_start", {"type": "message_start", "message": {
                "id": "msg_stub", "type": "message", "role": "assistant", "model": request.get("model"),
                "content": [], "stop_reason": None, "usage": {"input_tokens": len(prompt) // 4, "output_tokens": 1}}})
            pause(FIRST_TOKEN_MS)
            self.event("content_block_start",
                       {"type": "content_block_start", "index": 0, "content_block": {"type": "text", "text": ""}})
            words = ANSWER.split(" ")
            # About one token per word and a half; each delta carries a few words, as Claude's do
            for start in range(0, len(words), 3):
                chunk = " ".join(words[start:start + 3]) + ("" if start + 3 >= len(words) else " ")
                self.event("content_block_delta", {"type": "content_block_delta", "index": 0,
                                                   "delta": {"type": "text_delta", "text": chunk}})
                time.sleep(4.5 / TOKENS_PER_SECOND)
            self.event("content_block_stop", {"type": "content_block_stop", "index": 0})
            self.event("message_delta", {"type": "message_delta",
                                         "delta": {"stop_reason": "end_turn", "stop_sequence": None},
                                         "usage": {"output_tokens": int(len(words) * 1.5)}})
            self.event("message_stop", {"type": "message_stop"})
            with lock:
                counts["answers"] += 1
        finally:
            with lock:
                counts["open"] -= 1

    def event(self, name, data):
        self.wfile.write(f"event: {name}\ndata: {json.dumps(data)}\n\n".encode())
        self.wfile.flush()


if __name__ == "__main__":
    server = ThreadingHTTPServer(("0.0.0.0", 8080), Handler)
    server.daemon_threads = True
    server.serve_forever()
