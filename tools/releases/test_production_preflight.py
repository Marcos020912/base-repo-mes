import tempfile,unittest
from pathlib import Path
from production_preflight import inspect
class PreflightTest(unittest.TestCase):
 def check(self,text,strict=False):
  with tempfile.TemporaryDirectory() as folder:
   p=Path(folder)/'config';p.write_text(text);return inspect(p,strict)
 def valid(self):
  return '\n'.join(['repo.auth.enabled=true','repo.auth.jwtSecret='+'x'*48,'spring.datasource.url=jdbc:postgresql://localhost/db','spring.datasource.password=private-fixture','spring.jpa.hibernate.ddl-auto=validate','repo.basepath=file:/srv/data/','repo.public-domain=repo.example.org','repo.security.allowedOriginPattern=https://repo.example.org','server.forward-headers-strategy=framework','repo.deploy.haproxy-network=10.0.0.1','spring.mail.host=mail.example.org','repo.mail.from=repo@example.org','spring.mail.properties.mail.smtp.starttls.required=true','repo.privacy.require-assessment=true','repo.search.enabled=true'])
 def test_valid(self):self.assertTrue(self.check(self.valid())['ready'])
 def test_duplicates_and_secrets_redacted(self):
  result=self.check(self.valid()+'\nspring.datasource.password=SECRET_SENTINEL')
  self.assertFalse(result['ready']);self.assertNotIn('SECRET_SENTINEL',str(result))
 def test_destructive_schema(self):self.assertFalse(self.check(self.valid().replace('ddl-auto=validate','ddl-auto=create-drop'))['ready'])
 def test_capture_and_wildcard(self):self.assertFalse(self.check(self.valid()+'\nrepo.mail.delivery-mode=LOCAL_CAPTURE\nspring.mail.properties.mail.smtp.ssl.trust=*')['ready'])
 def test_public_template_jwt_is_rejected_and_redacted(self):
  secret='vkfvoswsohwrxgjaxipuiyyjgubggzdaqrcuupbugxtnalhiegkppdgjgwxsmvdb'
  result=self.check(self.valid().replace('x'*48,secret))
  self.assertFalse(result['ready']);self.assertNotIn(secret,str(result))
 def test_strict_schema(self):
  text=self.valid().replace('ddl-auto=validate','ddl-auto=update');self.assertTrue(self.check(text)['ready']);self.assertFalse(self.check(text,True)['ready'])
if __name__=='__main__':unittest.main()
