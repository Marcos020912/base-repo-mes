"""Isolated updater orchestration; no network, services or real checkout."""
import os
import pathlib
import shutil
import subprocess
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[2]

class UpdateSourceTest(unittest.TestCase):
    def run_fixture(self, **variables):
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            text = (ROOT / 'update.sh').read_text()
            # Only the privileged launcher check is bypassed in this disposable fixture.
            text = text.replace('[[ $EUID -eq 0 ]]', 'true')
            (root / 'update.sh').write_text(text)
            (root / 'config').mkdir()
            config = root / 'config/application.properties'
            config.write_text('fixture-private-config\n')
            (root / 'tools/releases').mkdir(parents=True)
            (root / 'tools/releases/production_preflight.py').write_text(
                'import os,sys;sys.exit(int(os.getenv("PREFLIGHT_FAIL","0")))\n')
            (root / 'build/libs').mkdir(parents=True)
            (root / 'build/libs/base-repo.jar').write_text('previous')
            (root / 'deploy.sh').write_text('''#!/bin/bash
set -eu
[[ "$1" == --update-from-source && "$2" == --inherited-lock ]]
[[ "$(readlink /proc/$$/fd/9)" == "$PWD/.update.lock" ]]
flock -n 9
printf 'deploy-called\\n' >> "$TRACE"
exit "${DEPLOY_FAIL:-0}"
''')
            (root / 'fakebin').mkdir()
            git = root / 'fakebin/git'
            git.write_text('''#!/bin/bash
printf '%s\\n' "$*" >> "$TRACE"
case "$1" in
 status) exit 0 ;;
 fetch) exit 0 ;;
 rev-parse) echo fixture-commit ;;
 merge-base) exit "${DIVERGENT:-0}" ;;
 merge) exit 0 ;;
 *) exit 2 ;;
esac
''')
            git.chmod(0o755)
            trace = root / 'trace'
            env = dict(os.environ, PATH=str(root / 'fakebin') + ':' + os.environ['PATH'], TRACE=str(trace), **variables)
            result = subprocess.run(['script', '-qec', f'bash {root}/update.sh', '/dev/null'],
                                    input='SI\n', text=True, capture_output=True, env=env, timeout=10)
            log = trace.read_text() if trace.exists() else ''
            self.assertEqual(config.read_text(), 'fixture-private-config\n')
            backups = list((root / '.releases').glob('*/previous.jar'))
            if backups:
                self.assertEqual(backups[0].read_text(), 'previous')
            return result.returncode, log

    @unittest.skipUnless(shutil.which('script'), 'requires util-linux script')
    def test_fetch_fast_forward_then_deploy_with_shared_lock(self):
        code, log = self.run_fixture()
        self.assertEqual(code, 0)
        self.assertIn('refs/heads/main', log)
        self.assertLess(log.index('merge --ff-only'), log.index('deploy-called'))

    def test_divergence_does_not_merge_or_deploy(self):
        code, log = self.run_fixture(DIVERGENT='1')
        self.assertNotEqual(code, 0)
        self.assertNotIn('merge --ff-only', log)
        self.assertNotIn('deploy-called', log)

    def test_preflight_failure_prevents_fetch(self):
        code, log = self.run_fixture(PREFLIGHT_FAIL='1')
        self.assertNotEqual(code, 0)
        self.assertNotIn('fetch ', log)

    def test_deploy_failure_is_not_reported_as_success(self):
        code, log = self.run_fixture(DEPLOY_FAIL='1')
        self.assertNotEqual(code, 0)
        self.assertIn('deploy-called', log)

    def test_background_java_does_not_keep_deployment_lock(self):
        for name in ('deploy.sh', 'update.sh'):
            line = next(line for line in (ROOT / name).read_text().splitlines() if line.startswith('nohup java'))
            self.assertIn('9>&- &', line)

    def test_shell_syntax(self):
        subprocess.run(['bash', '-n', str(ROOT / 'update.sh'), str(ROOT / 'deploy.sh')], check=True)

if __name__ == '__main__':
    unittest.main()
