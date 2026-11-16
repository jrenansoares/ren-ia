# Retained POSIX integration check for the Claude Code bootstrap.
import json, os, pathlib, stat, subprocess, sys, tempfile

code = pathlib.Path("app/src/main/resources/projetoia-claude/bootstrap.py").read_text(encoding="utf-8")
token = "b" * 64
url = "http://127.0.0.1:41234"
sid = "550e8400-e29b-41d4-a716-446655440000"

with tempfile.TemporaryDirectory() as tmpdir:
    tmp = pathlib.Path(tmpdir)
    bin_dir = tmp / "bin"
    bin_dir.mkdir()
    claude_sh = bin_dir / "claude"
    claude_sh.write_text(
        "#!/usr/bin/env python3\n"
        "import json, os, sys\n"
        "out = os.path.join(os.environ['HOME'], 'captured.json')\n"
        "with open(out, 'w') as f:\n"
        "    json.dump({'argv': sys.argv[1:], 'env': dict(os.environ)}, f)\n",
        encoding="utf-8",
    )
    claude_sh.chmod(claude_sh.stat().st_mode | stat.S_IXUSR)

    sess_dir = tmp / ".claude" / "projects" / "ws"
    sess_dir.mkdir(parents=True)
    sess_file = sess_dir / f"{sid}.jsonl"
    sess_file.write_text("stale\n", encoding="utf-8")

    base_env = {"HOME": str(tmp), "PATH": f"{bin_dir}:{os.environ['PATH']}", "TERM": "xterm-256color"}

    r1 = subprocess.run(
        [sys.executable, "-I", "-S", "-c", code],
        input=(json.dumps({
            "url": url, "token": token, "system": "sys", "session_id": sid,
            "resume_session": False, "reset_session": True,
            "model": "anthropic/claude-sonnet-4.6",
        }) + "\n").encode(),
        capture_output=True, env=base_env, check=True,
    )
    c1 = json.loads((tmp / "captured.json").read_text())
    assert not sess_file.exists()
    assert "--session-id" in c1["argv"] and "--resume" not in c1["argv"]
    assert "--model" in c1["argv"] and "anthropic/claude-sonnet-4.6" in c1["argv"]
    assert "--fallback-model" not in c1["argv"]
    assert "projeto-free" not in json.dumps(c1)

    sess_file.write_text('{"type":"user"}\n', encoding="utf-8")
    r2 = subprocess.run(
        [sys.executable, "-I", "-S", "-c", code],
        input=(json.dumps({
            "url": url, "token": token, "system": "sys", "session_id": sid,
            "resume_session": True, "reset_session": False,
            "model": "anthropic/claude-sonnet-4.6",
        }) + "\n").encode(),
        capture_output=True, env=base_env, check=True,
    )
    c2 = json.loads((tmp / "captured.json").read_text())
    assert "--resume" in c2["argv"] and "--session-id" not in c2["argv"]
    assert sess_file.exists()
    print("POSIX_BOOTSTRAP_OK")
