import json
import os
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
env = {key: value for key, value in os.environ.items()
       if key in {"HOME", "PATH", "TERM", "LANG", "LC_ALL", "TMPDIR", "PROOT_TMP_DIR"}}
env.update({
    "ANTHROPIC_BASE_URL": url,
    "ANTHROPIC_API_KEY": token,
    "ANTHROPIC_AUTH_TOKEN": token,
    "ANTHROPIC_MODEL": "projeto-free",
    "ANTHROPIC_DEFAULT_SONNET_MODEL": "projeto-free",
    "ANTHROPIC_DEFAULT_HAIKU_MODEL": "projeto-free",
    "ANTHROPIC_DEFAULT_OPUS_MODEL": "projeto-free",
    "CLAUDE_CODE_SUBAGENT_MODEL": "projeto-free",
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
        "--model", "projeto-free", "--fallback-model", "projeto-free",
        "--max-turns", "24", "--no-session-persistence", "--setting-sources=",
        "--strict-mcp-config", "--mcp-config", '{"mcpServers":{}}',
        "--tools", "Read,Write,Edit,Bash,Glob,Grep",
        "--append-system-prompt", config["system"]]
os.execvpe("claude", args, env)
