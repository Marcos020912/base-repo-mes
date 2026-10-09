import hashlib
import os
import subprocess
import sys
from pathlib import Path
import tempfile
import unittest
import zipfile
from verify_artifact import verify
class ArtifactTest(unittest.TestCase):
    def artifact(self, folder, extra=None):
        file=Path(folder)/'candidate.jar'
        with zipfile.ZipFile(file,'w') as jar:
            jar.writestr('META-INF/MANIFEST.MF','Manifest-Version: 1.0\n')
            jar.writestr('BOOT-INF/classes/fixture.txt','test')
            for name,value in (extra or {}).items():jar.writestr(name,value)
        return file,hashlib.sha256(file.read_bytes()).hexdigest()
    def test_valid_structure_checksum_and_no_writes(self):
        with tempfile.TemporaryDirectory() as folder:
            file,digest=self.artifact(folder);before=file.read_bytes();result=verify(file,digest.upper())
            self.assertEqual(0,result['count']);self.assertEqual(before,file.read_bytes());self.assertEqual(2,result['scannedApplicationEntries'])
            for bad in ['','not-a-hash','0'*64]:
                with self.assertRaises(ValueError):verify(file,bad)
    def test_embedded_configuration_and_literal_secret_are_rejected_without_values(self):
        with tempfile.TemporaryDirectory() as folder:
            file,digest=self.artifact(folder,{'BOOT-INF/classes/application.properties':'spring.mail.password=synthetic-credential\n'})
            result=verify(file,digest);self.assertEqual(2,result['count']);self.assertNotIn('synthetic-credential',str(result))
    def test_unsafe_paths_rejected(self):
        with tempfile.TemporaryDirectory() as folder:
            file,digest=self.artifact(folder,{'../escape.txt':'bad'})
            with self.assertRaises(ValueError):verify(file,digest)
    def test_candidate_socket_belongs_to_exact_pid(self):
        source=(Path(__file__).resolve().parents[2]/'update.sh').read_text()
        function=source[source.index('candidate_listens(){'):source.index('for _ in $(seq 1 90)')]
        code="import socket,time; s=socket.socket(); s.bind(('127.0.0.1',0)); s.listen(); print(s.getsockname()[1],flush=True); time.sleep(30)"
        process=subprocess.Popen([sys.executable,'-u','-c',code],stdout=subprocess.PIPE,text=True)
        try:
            port=int(process.stdout.readline())
            for pid,expected in [(process.pid,0),(os.getpid(),1)]:
                result=subprocess.run(['bash','-o','pipefail','-c',function+'\ncandidate_listens'],env={**os.environ,'CHECK_PORT':str(port),'PID':str(pid)},timeout=5)
                self.assertEqual(expected,result.returncode)
        finally:
            process.terminate();process.wait(timeout=5);process.stdout.close()
    def test_update_verifies_before_fetch_or_stopping_service(self):
        source=(Path(__file__).resolve().parents[2]/'update.sh').read_text().split('[[ $# == 3 ]]',1)[1]
        self.assertLess(source.index('tools/releases/verify_artifact.py'),source.index('git fetch'))
        self.assertLess(source.index('tools/releases/verify_artifact.py'),source.index('kill -TERM'))
        self.assertNotIn('eval ',source)
        self.assertIn('if candidate_listens && [[',source)
        self.assertLess(source.index('CHECK_PORT='),source.index('kill -TERM'))
if __name__=='__main__':unittest.main()
