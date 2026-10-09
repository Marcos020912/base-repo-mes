from pathlib import Path
import os
import subprocess
import tempfile
import unittest
ROOT=Path(__file__).resolve().parents[2]
class DeploymentMetricsTest(unittest.TestCase):
 def test_managed_metrics_properties_are_removed_before_replacement(self):
  source=(ROOT/'deploy.sh').read_text();function='remove_managed_properties(){'+source.split('remove_managed_properties(){',1)[1].split('configure_gradle_proxy(){',1)[0]
  with tempfile.TemporaryDirectory() as folder:
   conf=Path(folder)/'application.properties';conf.write_text('repo.metrics.datacite.enabled=false\nrepo.metrics.datacite.enabled: true\nrepo.metrics.datacite.repository-id=da-old\nrepo.metrics.datacite.repository-id: da-new\nrepo.unmanaged=keep\n')
   subprocess.run(['bash','-c',function+'\nremove_managed_properties'],env={**os.environ,'CONF':str(conf)},check=True)
   self.assertEqual('repo.unmanaged=keep\n',conf.read_text())
 def test_settings_are_loaded_before_reuse_branch_and_no_automatic_enabling(self):
  source=(ROOT/'deploy.sh').read_text();self.assertLess(source.index('DATACITE_USAGE_ENABLED="$(boolean_value'),source.index('if [[ "${REUSE_CONFIGURATION,,}"'))
  self.assertIn('DATACITE_USAGE_ENABLED=${DATACITE_USAGE_ENABLED:-false}',source);self.assertIn('repo.metrics.datacite.enabled: $DATACITE_USAGE_ENABLED',source)
if __name__=='__main__':unittest.main()
