#!/usr/bin/env python3
"""Validate source JSON catalogues and generate a deterministic static bundle."""
import argparse
import json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
STATIC=ROOT/'src/main/resources/static'
def render():
    catalogues={lang:json.loads((STATIC/'locales'/f'{lang}.json').read_text()) for lang in ('es','en')}
    if set(catalogues['es'])!=set(catalogues['en']):raise ValueError('Locale key sets differ')
    for catalogue in catalogues.values():
        if any(not isinstance(value,str) or not value.strip() for value in catalogue.values()):raise ValueError('Empty or non-text translation')
    return '// Generated from locales/*.json by tools/i18n/build_catalogues.py; do not edit.\nwindow.uiCatalogues = Object.freeze('+json.dumps(catalogues,ensure_ascii=False,sort_keys=True,separators=(',',':'))+');\n'
if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--check',action='store_true');args=parser.parse_args();target=STATIC/'ui-locales.js';content=render()
    if args.check:
        if not target.exists() or target.read_text()!=content:raise SystemExit('Outdated locale bundle; run build_catalogues.py')
    else:target.write_text(content)
