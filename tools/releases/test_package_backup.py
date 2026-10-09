import hashlib
from pathlib import Path
import json
import stat
import tarfile
import tempfile
import unittest
from unittest.mock import patch
from package_backup import package
class BackupTest(unittest.TestCase):
    def setup(self, folder):
        root=Path(folder);data=root/'data';data.mkdir();(data/'description.md').write_text('# Test')
        dump=root/'fixture.dump';dump.write_bytes(b'PGDMP fixture');conf=root/'application.properties';conf.write_text('private config')
        return dump,data,conf,root/'backup.tar.gz'
    @patch('package_backup.subprocess.run')
    def test_private_manifest_exact_content_no_overwrite(self, run):
        with tempfile.TemporaryDirectory() as folder:
            args=self.setup(folder);package(*args,writes_stopped=True)
            self.assertEqual(0o600,stat.S_IMODE(args[3].stat().st_mode))
            with tarfile.open(args[3]) as archive:
                manifest=json.load(archive.extractfile('manifest.json'))
                for entry in manifest['files']:
                    content=archive.extractfile(entry['path']).read();self.assertEqual(entry['sha256'],hashlib.sha256(content).hexdigest());self.assertEqual(entry['size'],len(content))
                self.assertEqual(b'private config',archive.extractfile('config/application.properties').read())
            with self.assertRaises(FileExistsError):package(*args,writes_stopped=True)
            self.assertEqual(['pg_restore','--list',str(args[0])],run.call_args.args[0])
    def test_requires_quiescence_and_rejects_links_and_recursive_destination(self):
        with tempfile.TemporaryDirectory() as folder:
            args=self.setup(folder)
            with self.assertRaises(ValueError):package(*args)
            with self.assertRaises(ValueError):package(*args[:3],args[1]/'backup.gz',writes_stopped=True)
            (args[1]/'link').symlink_to(args[2])
            with self.assertRaises(ValueError):package(*args,writes_stopped=True)
if __name__=='__main__':unittest.main()
