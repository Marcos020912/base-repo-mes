import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
SOURCE = (ROOT/'tools/releases/build_candidate.sh').read_text()
SETUP = SOURCE.split('# Gradle 8.12.1', 1)[1].split('COMMIT=', 1)[0]
SETUP = '# Gradle 8.12.1' + SETUP

class CandidateJavaTest(unittest.TestCase):
    def run_java(self, version):
        with tempfile.TemporaryDirectory() as directory:
            home = Path(directory)
            (home/'bin').mkdir()
            java = home/'bin/java'
            java.write_text(f'#!/bin/sh\necho \'openjdk version "{version}"\' >&2\n')
            java.chmod(0o755)
            return subprocess.run(['bash', '-c', 'set -euo pipefail\n' + SETUP],
                                  env={**os.environ, 'JAVA_HOME':directory}, text=True, capture_output=True)

    def test_accepts_jdk21(self):
        self.assertEqual(self.run_java('21.0.9').returncode, 0)

    def test_rejects_jdk25_before_build(self):
        result = self.run_java('25.0.4')
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('Se requiere JDK21', result.stderr)

    def test_rejects_similar_version(self):
        self.assertNotEqual(self.run_java('210.0.1').returncode, 0)

if __name__ == '__main__': unittest.main()
