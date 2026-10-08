import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT=Path(__file__).resolve().parents[2]
FUNCTION=(ROOT/'deploy.sh').read_text().split('property_value(){',1)[1].split('boolean_value(){',1)[0]
SCRIPT='property_value(){'+FUNCTION+'\nproperty_value "spring.datasource.password"'

class DeploymentPlaceholderTest(unittest.TestCase):
    def resolve(self,value,environment=None):
        with tempfile.TemporaryDirectory() as folder:
            conf=Path(folder)/'configuration.properties'
            conf.write_text('spring.datasource.password: '+value+'\n')
            env=dict(os.environ,CONF=str(conf));env.pop('SYNTHETIC_REPO_PASSWORD',None)
            env.update(environment or {})
            return subprocess.check_output(['bash','-c',SCRIPT],env=env,text=True).rstrip('\n')
    def test_empty_placeholder_does_not_become_database_password(self):
        self.assertEqual(self.resolve('${SYNTHETIC_REPO_PASSWORD:}'),'')
        self.assertEqual(self.resolve('${SYNTHETIC_REPO_PASSWORD}'),'')
    def test_environment_and_fallback(self):
        self.assertEqual(self.resolve('${SYNTHETIC_REPO_PASSWORD:fallback}'),'fallback')
        self.assertEqual(self.resolve('${SYNTHETIC_REPO_PASSWORD:fallback}',{'SYNTHETIC_REPO_PASSWORD':'selected'}),'selected')
        self.assertEqual(self.resolve('${SYNTHETIC_REPO_PASSWORD:fallback}',{'SYNTHETIC_REPO_PASSWORD':''}),'')
    def test_literals_are_not_executed(self):
        self.assertEqual(self.resolve('$(printf injected)'), '$(printf injected)')
        self.assertEqual(self.resolve('literal-value'), 'literal-value')

if __name__=='__main__':unittest.main()
