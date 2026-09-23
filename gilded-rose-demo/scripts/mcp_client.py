#!/usr/bin/env python3
"""Minimal stdio MCP client for driving the java-refactoring MCP server.

Used by scripts/verify-recipe.sh to deterministically exercise the refactoring
tools against the Gilded Rose kata (analyze-then-apply semantics are handled
by the script; this client just speaks JSON-RPC).
"""
import json
import subprocess
import sys

JAR = "/workspace/refactor-mcp/target/refactor-mcp-0.1.2-fat.jar"


class McpClient:
    def __init__(self, jar=JAR):
        self.proc = subprocess.Popen(
            ["java", "-jar", jar],
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL,
        )
        self._id = 0
        self._send({
            "jsonrpc": "2.0",
            "id": 1,
            "method": "initialize",
            "params": {
                "protocolVersion": "2024-11-05",
                "capabilities": {},
                "clientInfo": {"name": "verify", "version": "1"},
            },
        })
        self._recv()
        self._send({"jsonrpc": "2.0", "method": "notifications/initialized"})

    def _send(self, obj):
        self.proc.stdin.write((json.dumps(obj) + "\n").encode())
        self.proc.stdin.flush()

    def _recv(self):
        line = self.proc.stdout.readline()
        if not line:
            raise RuntimeError("MCP server closed stdout")
        return json.loads(line)

    def call(self, tool, args):
        self._id += 1
        self._send({
            "jsonrpc": "2.0",
            "id": self._id,
            "method": "tools/call",
            "params": {"name": tool, "arguments": args},
        })
        msg = self._recv()
        assert msg.get("id") == self._id, msg
        if "error" in msg:
            return {"isError": True, "text": "RPC error: " + msg["error"].get("message", str(msg["error"]))}
        result = msg["result"]
        text = "".join(c.get("text", "") for c in result.get("content", []))
        return {"isError": result.get("isError", False), "text": text}

    def close(self):
        self.proc.terminate()


def write_file(path, content):
    import pathlib
    p = pathlib.Path(path)
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(content)


if __name__ == "__main__":
    import argparse

    parser = argparse.ArgumentParser()
    parser.add_argument("tool")
    parser.add_argument("--arg", action="append", default=[],
                        help="key=value pairs")
    args = parser.parse_args()

    kv = {}
    for a in args.arg:
        k, _, v = a.partition("=")
        try:
            kv[k] = json.loads(v)
        except json.JSONDecodeError:
            kv[k] = v

    client = McpClient()
    try:
        out = client.call(args.tool, kv)
        print("ERROR:" if out["isError"] else "OK:")
        print(out["text"])
    finally:
        client.close()