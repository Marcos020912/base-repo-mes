import contextlib
import io
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from audit_secrets import inspect, audit

class SecretAuditTest(unittest.TestCase):
    def test_safe_placeholders_and_comment_are_not_credentials(self):
        source=b'# spring.mail.password=example\nspring.mail.password=${SMTP_PASSWORD:}\nrepo.auth.jwtSecret=${JWT_SECRET}\nrepo.datacite.password=\n'
        self.assertEqual(inspect(source,'config/application-default.properties'),[])

    def test_literal_defaults_and_nonempty_placeholder_fallback_fail(self):
        findings=inspect(b'spring.mail.password=synthetic-example\nrepo.auth.jwtSecret=${JWT_SECRET:unsafe-fallback}\n','config/application-default.properties')
        self.assertEqual(len(findings),2)
        self.assertNotIn('synthetic-example',json.dumps(findings))
        self.assertNotIn('unsafe-fallback',json.dumps(findings))

    def test_known_tokens_and_private_keys_are_redacted_even_in_tests(self):
        token='ghp_'+'A'*36
        key='-----BEGIN '+'PRIVATE KEY-----'
        source=(token+'\n'+key).encode()
        findings=inspect(source,'src/test/fixture.txt')
        self.assertEqual({item['rule'] for item in findings},{'github-token','private-key'})
        self.assertNotIn(token,json.dumps(findings))
        self.assertNotIn(key,json.dumps(findings))
        self.assertEqual(inspect(b'\x00'+source,'binary.dat'),[])

    def test_historical_deleted_credentials_are_detected_read_only(self):
        old=os.getcwd()
        with tempfile.TemporaryDirectory() as folder:
            try:
                os.chdir(folder)
                def git(*args):return subprocess.check_output(['git',*args],stderr=subprocess.DEVNULL)
                git('init');git('config','user.name','Synthetic');git('config','user.email','synthetic@example.invalid')
                Path('app.properties').write_text('spring.mail.password=synthetic-example\n')
                git('add','.');git('commit','-m','fixture')
                Path('app.properties').write_text('spring.mail.password=${SMTP_PASSWORD:}\n')
                git('add','.');git('commit','-m','remove fixture')
                before=git('rev-parse','HEAD');self.assertEqual(audit(),[])
                findings=audit(True);self.assertEqual(len(findings),1)
                self.assertEqual(findings[0]['scope'],'history')
                self.assertEqual(git('rev-parse','HEAD'),before)
                self.assertEqual(git('status','--porcelain'),b'')
            finally:os.chdir(old)

if __name__=='__main__':unittest.main()
