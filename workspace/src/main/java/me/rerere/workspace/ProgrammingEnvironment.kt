package me.rerere.workspace

/** Fixed commands only: project names and chat input never become setup shell text. */
object ProgrammingEnvironment {
    const val INSTALL_TIMEOUT_MILLIS = 30 * 60 * 1_000L
    const val ROOTFS_URL = "https://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/ubuntu-base-24.04.5-base-arm64.tar.gz"
    const val ROOTFS_SHA256 = "a91d5a93010193712d346d761372b7c9db6dfcf093893161c64ca107f05914f2"

    val checkCommand = """
        set -eu
        command -v git >/dev/null
        command -v python3 >/dev/null
        command -v node >/dev/null
        command -v npm >/dev/null
        printf '%s\n' 'PROJETO_IA_ENVIRONMENT_OK'
    """.trimIndent()

    val installCommand = """
        set -eu
        command -v apt-get >/dev/null || { printf '%s\n' 'Ubuntu/Debian with apt is required' >&2; exit 2; }
        export DEBIAN_FRONTEND=noninteractive
        export LC_ALL=C
        printf '%s\n' 'PROJETO_IA_SETUP:REPAIR'
        dpkg --configure -a
        printf '%s\n' 'PROJETO_IA_SETUP:INDEX'
        apt-get -o APT::Status-Fd=1 -o APT::Update::Error-Mode=any update
        printf '%s\n' 'PROJETO_IA_SETUP:PACKAGES'
        apt-get -o APT::Status-Fd=1 -o Dpkg::Use-Pty=0 install -y --no-install-recommends ca-certificates git python3 python3-venv nodejs npm curl gnupg
        printf '%s\n' 'PROJETO_IA_SETUP:DONE'
    """.trimIndent()

    val smokeTestCommand = """
        set -eu
        python3 -I -S - <<'PROJETO_IA_SMOKE_TEST'
        import os, pathlib, subprocess, tempfile
        git_env = {key: value for key, value in os.environ.items() if not key.startswith('GIT_')}
        git_env.update(GIT_CONFIG_NOSYSTEM='1', GIT_CONFIG_GLOBAL='/dev/null')
        node_env = {key: value for key, value in os.environ.items() if key not in ('NODE_OPTIONS', 'NODE_PATH')}
        with tempfile.TemporaryDirectory(prefix='.projetoia-check-', dir='.') as folder:
            root = pathlib.Path(folder).resolve()
            source = root / 'calculate.py'
            source.write_text('def add(a, b):\n    return a - b\n')
            before = source.read_text()
            assert 'return a - b' in before
            source.write_text(before.replace('return a - b', 'return a + b'))
            (root / 'test_calculate.py').write_text('import unittest\nfrom calculate import add\nclass TestAdd(unittest.TestCase):\n    def test_add(self):\n        self.assertEqual(add(2, 3), 5)\n')
            subprocess.run(['python3', '-E', '-S', '-m', 'unittest', '-v'], cwd=root, check=True)
            subprocess.run(['git', '-c', 'core.hooksPath=/dev/null', 'init', '--quiet'], cwd=root, env=git_env, check=True)
            subprocess.run(['git', '-c', 'core.fsmonitor=false', 'status', '--porcelain'], cwd=root, env=git_env, check=True, stdout=subprocess.PIPE)
            subprocess.run(['node', '-e', 'if (2 + 3 !== 5) process.exit(1)'], cwd=root, env=node_env, check=True)
        print('PROJETO_IA_SMOKE_TEST_OK')
        PROJETO_IA_SMOKE_TEST
    """.trimIndent()

    fun isReady(result: WorkspaceCommandResult): Boolean =
        result.exitCode == 0 && !result.timedOut && !result.truncated &&
            result.stdout.lineSequence().any { it == "PROJETO_IA_ENVIRONMENT_OK" }

    fun smokeTestPassed(result: WorkspaceCommandResult): Boolean =
        result.exitCode == 0 && !result.timedOut && !result.truncated &&
            result.stdout.lineSequence().any { it == "PROJETO_IA_SMOKE_TEST_OK" }
}
