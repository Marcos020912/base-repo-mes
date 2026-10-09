import http.server
import json
from pathlib import Path
import ssl
import subprocess
import tempfile
import threading
import unittest
from unittest.mock import patch,MagicMock
from validate_external import Validator

class Handler(http.server.BaseHTTPRequestHandler):
 def log_message(self,*args):pass
 def do_GET(self):
  if self.path=='/login.html' or self.path.startswith('/datasets/'):
   body=b'<html><head><title>Fixture</title></head><body><form id="login-form"></form><script src="login.js"></script></body></html>';content='text/html'
  elif self.path.startswith('/api/v1/public/resources/'):
   body=json.dumps({'id':'r','doi':'10.1234/example'}).encode();content='application/json'
  else:self.send_error(404);return
  self.send_response(200);self.send_header('Content-Type',content);self.end_headers();self.wfile.write(body)
 def do_OPTIONS(self):
  self.send_response(204);self.send_header('Access-Control-Allow-Origin',self.headers['Origin']);self.send_header('Access-Control-Allow-Methods','POST');self.end_headers()

class ExternalValidationTest(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  cls.folder=tempfile.TemporaryDirectory();folder=Path(cls.folder.name);cls.cert=folder/'cert.pem';key=folder/'key.pem'
  subprocess.run(['openssl','req','-x509','-newkey','rsa:2048','-nodes','-keyout',str(key),'-out',str(cls.cert),'-days','1','-subj','/CN=localhost','-addext','subjectAltName=DNS:localhost'],check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
  cls.server=http.server.ThreadingHTTPServer(('127.0.0.1',0),Handler);ctx=ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER);ctx.load_cert_chain(cls.cert,key);cls.server.socket=ctx.wrap_socket(cls.server.socket,server_side=True);cls.thread=threading.Thread(target=cls.server.serve_forever,daemon=True);cls.thread.start();cls.base='https://localhost:'+str(cls.server.server_port)
 @classmethod
 def tearDownClass(cls):cls.server.shutdown();cls.server.server_close();cls.folder.cleanup()
 def validator(self):return Validator(self.base,ca_file=str(self.cert))
 def test_real_https_with_explicit_ca_and_readonly_cors(self):
  v=self.validator();v.web();v.dataset('r');self.assertTrue(all(c['status']=='PASS' for c in v.checks));self.assertFalse(v.report()['certified']);self.assertTrue(v.report()['readOnly'])
 def test_hostname_mismatch_and_untrusted_certificate_fail(self):
  for v in [Validator(self.base),Validator(self.base.replace('localhost','127.0.0.1'),ca_file=str(self.cert))]:
   v.web();self.assertTrue(all(c['status']=='FAIL' for c in v.checks));self.assertNotIn('PRIVATE',json.dumps(v.report()))
 def test_invalid_targets_and_identifiers_rejected(self):
  for url in ['http://example.org','https://user:secret@example.org','https://example.org/?token=secret','https://example.org/path']:
   with self.assertRaises(ValueError):Validator(url)
  v=self.validator()
  with self.assertRaises(ValueError):v.dataset('../private')
  with self.assertRaises(ValueError):v.tracker('repository.account')
 def test_datacite_checks_match_identity_and_landing_without_writes(self):
  v=self.validator();original=v.request
  def request(url,*args,**kwargs):
   if url.startswith('https://api.datacite.org/'):
    return 200,{},json.dumps({'data':{'id':'10.1234/example','attributes':{'state':'findable','url':self.base+'/datasets/r','viewsOverTime':[],'downloadsOverTime':[]}}}).encode()
   if url.startswith('https://analytics.datacite.org/'):return 200,{},b'No events recorded'
   return original(url,*args,**kwargs)
  with patch.object(v,'request',side_effect=request) as calls:
   v.dataset('r','10.1234/example');v.tracker('da-example')
   self.assertTrue(all(c['status']=='PASS' for c in v.checks[:-1]));self.assertEqual('INCONCLUSIVE',v.checks[-1]['status']);self.assertTrue(all(len(c.args)==1 for c in calls.call_args_list))
 def test_same_origin_without_cors_headers_is_inconclusive_not_failed(self):
  v=self.validator()
  with patch.object(v,'request',return_value=(200,{},b'')):v.web()
  self.assertEqual('INCONCLUSIVE',v.checks[1]['status'])
  self.assertFalse(v.report()['certified'])
 def test_no_smtp_login_or_delivery(self):
  v=self.validator();smtp=MagicMock();smtp.__enter__.return_value=smtp;smtp.ehlo.return_value=(250,b'ok');smtp.sock.version.return_value='TLSv1.3'
  with patch('smtplib.SMTP',return_value=smtp):v.smtp('mail.example.org',25,'starttls')
  smtp.starttls.assert_called_once();smtp.login.assert_not_called();smtp.sendmail.assert_not_called();self.assertEqual('PASS',v.checks[0]['status'])
 def test_cli_private_output_and_no_overwrite(self):
  with tempfile.TemporaryDirectory() as folder:
   output=Path(folder)/'evidence.json';command=['python3',str(Path(__file__).with_name('validate_external.py')),'--base-url',self.base,'--ca-file',str(self.cert),'--output',str(output)]
   first=subprocess.run(command,capture_output=True,text=True);self.assertEqual(0,first.returncode,first.stderr);self.assertEqual(0o600,output.stat().st_mode&0o777);before=output.read_bytes();self.assertNotEqual(0,subprocess.run(command,capture_output=True).returncode);self.assertEqual(before,output.read_bytes())
if __name__=='__main__':unittest.main()
