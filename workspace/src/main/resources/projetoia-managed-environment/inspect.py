import json
import os
from pathlib import Path
import re
import shlex
import shutil
import subprocess
import sys


def command(args, timeout=20):
    return subprocess.run(args, capture_output=True, text=True, timeout=timeout)


release = {}
for line in Path('/etc/os-release').read_text().splitlines():
    if '=' in line and not line.startswith('#'):
        key, value = line.split('=', 1)
        parts = shlex.split(value)
        release[key] = parts[0] if parts else ''

result = dict(linuxVersion=release.get('PRETTY_NAME') or release.get('NAME') or 'Linux',
              claudeVersion=None, claudeUsable=False, claudeError=None,
              updatesChecked='--updates' in sys.argv, linuxPackageUpdates=None,
              claudeCandidate=None, claudeUpdateAvailable=False)
package = command(['dpkg-query', '-W', '-f=${Status}\n${Version}', 'claude-code'])
lines = package.stdout.splitlines()
if package.returncode == 0 and len(lines) == 2 and lines[0] == 'install ok installed':
    result['claudeVersion'] = lines[1].strip()
    executable = shutil.which('claude')
    if executable:
        try:
            env = dict(os.environ, DISABLE_UPDATES='1', DISABLE_AUTOUPDATER='1')
            probe = subprocess.run([executable, '--version'], capture_output=True, text=True, timeout=20, env=env)
            match = re.fullmatch(r'([0-9]+\.[0-9]+\.[0-9]+(?:-[A-Za-z0-9.-]+)?)\s+\(Claude Code\)', probe.stdout.strip())
            result['claudeUsable'] = probe.returncode == 0 and match is not None and (match[1] == lines[1] or lines[1].startswith(match[1] + '-'))
            if not result['claudeUsable']:
                result['claudeError'] = (probe.stderr.strip() or probe.stdout.strip() or 'Claude Code version test failed')[-2000:]
        except (OSError, subprocess.TimeoutExpired) as error:
            result['claudeError'] = str(error)[-2000:]
    else:
        result['claudeError'] = 'Claude Code executable is missing from PATH'

if result['updatesChecked']:
    plan = command(['apt-get', '-s', '--with-new-pkgs', 'upgrade'], timeout=60)
    if plan.returncode != 0:
        raise RuntimeError(plan.stderr.strip() or 'Unable to inspect Ubuntu package updates')
    packages = {line.split()[1].split(':')[0] for line in plan.stdout.splitlines() if line.startswith('Inst ')}
    result['linuxPackageUpdates'] = len(packages - {'claude-code'})
    policy = command(['apt-cache', 'policy', 'claude-code'])
    if policy.returncode != 0:
        raise RuntimeError(policy.stderr.strip() or 'Unable to inspect Claude Code updates')
    candidate = re.search(r'^\s*Candidate:\s*(\S+)', policy.stdout, flags=re.MULTILINE)
    if candidate and candidate[1] != '(none)':
        result['claudeCandidate'] = candidate[1]
    if result['claudeVersion'] and result['claudeCandidate']:
        comparison = command(['dpkg', '--compare-versions', result['claudeCandidate'], 'gt', result['claudeVersion']])
        if comparison.returncode not in (0, 1):
            raise RuntimeError('Unable to compare Claude Code package versions')
        result['claudeUpdateAvailable'] = comparison.returncode == 0

print('PROJETO_IA_MANAGED_ENV=' + json.dumps(result, separators=(',', ':')))
