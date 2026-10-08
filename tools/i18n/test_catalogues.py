import json
import re
import unittest
from html.parser import HTMLParser
from build_catalogues import render,STATIC
class Bindings(HTMLParser):
    def __init__(self):super().__init__();self.keys=set()
    def handle_starttag(self,tag,attrs):
        value=dict(attrs).get('data-i18n')
        if value:self.keys.add(value)
class CatalogueTest(unittest.TestCase):
    def test_generated_bundle_is_current_and_language_key_sets_match(self):
        self.assertEqual(render(),(STATIC/'ui-locales.js').read_text())
        es=json.loads((STATIC/'locales/es.json').read_text());en=json.loads((STATIC/'locales/en.json').read_text())
        self.assertEqual(es.keys(),en.keys())
        for key in es:self.assertEqual(set(re.findall(r'\{[\w]+\}',es[key])),set(re.findall(r'\{[\w]+\}',en[key])))
    def test_migrated_auth_bindings_reference_known_keys(self):
        keys=set(json.loads((STATIC/'locales/es.json').read_text()))
        used=set()
        for page in ('login','register','verify'):
            parser=Bindings();parser.feed((STATIC/f'{page}.html').read_text());used.update(parser.keys)
            script=(STATIC/f'{page}.js').read_text()
            used.update(re.findall(r"uiI18n\.(?:t|error)\('([^']+)'",script))
            used.update(re.findall(r"uiI18n\.set\([^,]+,'([^']+)'",script))
            used.update(re.findall(r"notifyKey\('([^']+)'",script))
            self.assertIn('ui-i18n.js',(STATIC/f'{page}.html').read_text())
        self.assertTrue(used);self.assertEqual(used-keys,set())
if __name__=='__main__':unittest.main()
