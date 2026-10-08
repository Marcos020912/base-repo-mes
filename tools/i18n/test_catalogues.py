import json
import re
import unittest
from html.parser import HTMLParser
from build_catalogues import render,STATIC
class Bindings(HTMLParser):
    def __init__(self):super().__init__();self.keys=set();self.stack=[]
    def handle_starttag(self,tag,attrs):
        if any(key for _,key in self.stack):raise ValueError('Text binding would remove nested markup/control')
        value=dict(attrs).get('data-i18n')
        if value:self.keys.add(value)
        if tag not in {'area','base','br','col','embed','hr','img','input','link','meta','param','source','track','wbr'}:self.stack.append((tag,value))
    def handle_endtag(self,tag):
        for index in range(len(self.stack)-1,-1,-1):
            if self.stack[index][0]==tag:self.stack=self.stack[:index];break
class CatalogueTest(unittest.TestCase):
    def test_bindings_cannot_replace_form_controls(self):
        with self.assertRaises(ValueError):Bindings().feed('<label data-i18n="username">Name<input name="username"></label>')
    def test_generated_bundle_is_current_and_language_key_sets_match(self):
        self.assertEqual(render(),(STATIC/'ui-locales.js').read_text())
        es=json.loads((STATIC/'locales/es.json').read_text());en=json.loads((STATIC/'locales/en.json').read_text())
        self.assertEqual(es.keys(),en.keys())
        for key in es:self.assertEqual(set(re.findall(r'\{[\w]+\}',es[key])),set(re.findall(r'\{[\w]+\}',en[key])))
    def test_migrated_auth_bindings_reference_known_keys(self):
        keys=set(json.loads((STATIC/'locales/es.json').read_text()))
        used=set()
        for page in ('login','register','verify','account','users'):
            parser=Bindings();parser.feed((STATIC/f'{page}.html').read_text());used.update(parser.keys)
            script=(STATIC/f'{page}.js').read_text()
            used.update(re.findall(r"uiI18n\.(?:t|error)\('([^']+)'",script))
            used.update(re.findall(r"uiI18n\.set\([^,]+,'([^']+)'",script))
            used.update(re.findall(r"notifyKey\('([^']+)'",script))
            self.assertIn('ui-i18n.js',(STATIC/f'{page}.html').read_text())
        self.assertTrue(used);self.assertEqual(used-keys,set())
    def test_shared_auth_pages_load_catalogues_before_shared_navigation(self):
        for page in STATIC.glob('*.html'):
            html=page.read_text()
            if 'src="auth.js"' in html:
                self.assertLess(html.index('src="ui-locales.js"'),html.index('src="ui-i18n.js"'))
                self.assertLess(html.index('src="ui-i18n.js"'),html.index('src="auth.js"'))
if __name__=='__main__':unittest.main()
