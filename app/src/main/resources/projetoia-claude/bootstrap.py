import json
import os
import re
import sys

# Read exactly the first line. Buffered read-ahead would swallow SDK control frames
# that the executable must read from the same stdin after exec.
line = bytearray()
while True:
    char = os.read(0, 1)
    if char == b"\n":
        break
    if not char or len(line) > 131072:
        raise SystemExit("Invalid Claude bootstrap")
    line.extend(char)
config = json.loads(line)
url = config["url"]
token = config["token"]
if not url.startswith("http://127.0.0.1:") or not token.isalnum() or len(token) != 64:
    raise SystemExit("Invalid local gateway")

raw_session_id = config.get("session_id") or config.get("sessionId")
session_id = str(raw_session_id).strip() if raw_session_id else None
if session_id and not re.fullmatch(r"[A-Za-z0-9-]{1,128}", session_id):
    raise SystemExit("Invalid session identifier")

model = str(config.get("model") or "projeto-free").strip() or "projeto-free"
raw_fallback = config.get("fallback_model")
if raw_fallback is None:
    fallback_model = "projeto-free" if model == "projeto-free" else ""
else:
    fallback_model = str(raw_fallback).strip()

sonnet_model = str(config.get("sonnet_model") or model).strip() or model
opus_model = str(config.get("opus_model") or model).strip() or model
haiku_model = str(config.get("haiku_model") or model).strip() or model
subagent_model = str(config.get("subagent_model") or haiku_model or model).strip() or model


def find_session_files(target_id):
    if not target_id:
        return []
    home = os.environ.get("HOME") or ""
    projects_dir = os.path.join(home, ".claude", "projects") if home else ""
    if not projects_dir or not os.path.isdir(projects_dir):
        return []
    target_name = f"{target_id}.jsonl"
    found = []
    for root, _, files in os.walk(projects_dir):
        if target_name in files:
            found.append(os.path.join(root, target_name))
    return found


env = {key: value for key, value in os.environ.items()
       if key in {"HOME", "PATH", "TERM", "LANG", "LC_ALL", "TMPDIR", "PROOT_TMP_DIR"}}
env.update({
    "ANTHROPIC_BASE_URL": url,
    "ANTHROPIC_API_KEY": token,
    "ANTHROPIC_AUTH_TOKEN": token,
    "ANTHROPIC_MODEL": model,
    "ANTHROPIC_DEFAULT_SONNET_MODEL": sonnet_model,
    "ANTHROPIC_DEFAULT_HAIKU_MODEL": haiku_model,
    "ANTHROPIC_DEFAULT_OPUS_MODEL": opus_model,
    "CLAUDE_CODE_SUBAGENT_MODEL": subagent_model,
    "CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC": "1",
    "DISABLE_AUTOUPDATER": "1",
    "DISABLE_UPDATES": "1",
    "DISABLE_TELEMETRY": "1",
    "DISABLE_ERROR_REPORTING": "1",
    "NO_PROXY": "127.0.0.1,localhost",
    "API_TIMEOUT_MS": "180000",
})

args = ["claude", "--bare", "--print", "--verbose",
        "--input-format", "stream-json", "--output-format", "stream-json",
        "--permission-prompt-tool", "stdio", "--permission-mode", "default",
        "--model", model]
if fallback_model:
    args.extend(["--fallback-model", fallback_model])
args.extend([
    "--max-turns", "24", "--setting-sources=",
    "--strict-mcp-config", "--mcp-config", '{"mcpServers":{}}',
    "--tools", "Read,Write,Edit,Bash,Glob,Grep",
    "--append-system-prompt", config["system"],
])

resume_requested = bool(config.get("resume_session"))
reset_requested = bool(config.get("reset_session"))
if session_id:
    session_files = find_session_files(session_id)
    has_existing = any(
        os.path.isfile(path) and os.path.getsize(path) > 0
        for path in session_files
    )
    if resume_requested and has_existing and not reset_requested:
        args.extend(["--resume", session_id])
    else:
        for stale in session_files:
            try:
                os.remove(stale)
            except OSError:
                pass
        args.extend(["--session-id", session_id])
else:
    args.append("--no-session-persistence")

os.execvpe("claude", args, env)
