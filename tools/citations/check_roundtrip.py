#!/usr/bin/env python3
"""Check actual Java citation exports with independent pinned bibliographic parsers."""
import argparse
import json
from pathlib import Path
import pybtex.database
import bibtexparser
import rispy


def check(directory):
    expected = json.loads((directory / 'expected.json').read_text(encoding='utf-8'))
    bib = pybtex.database.parse_file(str(directory / 'citation.bibtex'), bib_format='bibtex')
    assert len(bib.entries) == 1
    entry = next(iter(bib.entries.values()))
    assert entry.type == 'dataset'
    for field in ['doi', 'version', 'year', 'publisher']:
        assert entry.fields[field] == expected[field], field
    people = entry.persons['author']
    assert len(people) == 2
    assert ' '.join(people[0].first_names + people[0].middle_names) == expected['personGiven']
    assert ' '.join(people[0].last_names) == expected['personFamily']
    assert ' '.join(people[1].last_names) == '{' + expected['organization'] + '}'
    # BibTeX stores TeX escapes intentionally; round-trip must not drop or split them.
    parser = bibtexparser.bparser.BibTexParser(common_strings=True)
    parser.ignore_nonstandard_types = False
    raw = bibtexparser.loads((directory / 'citation.bibtex').read_text(encoding='utf-8'), parser=parser)
    assert len(raw.entries) == 1
    parser_again = bibtexparser.bparser.BibTexParser(common_strings=True)
    parser_again.ignore_nonstandard_types = False
    parsed_again = bibtexparser.loads(bibtexparser.dumps(raw), parser=parser_again)
    assert parsed_again.entries == raw.entries
    assert r'\{Cuba\}' in entry.fields['title']
    assert r'50\% \& x\_y \#1 \$2' in entry.fields['title']
    assert r'\textbackslash{}ruta' in entry.fields['title']
    ris = rispy.load(directory / 'citation.ris', encoding='utf-8')
    assert len(ris) == 1
    item = ris[0]
    assert item['type_of_reference'] == 'DATA'
    for field in ['title', 'doi', 'year', 'publisher']:
        assert item[field] == expected[field], field
    assert item['edition'] == expected['version']
    assert item['authors'] == [expected['personFamily'] + ', ' + expected['personGiven'], expected['organization']]
    assert rispy.loads(rispy.dumps(ris)) == ris
    csl = json.loads((directory / 'citation.csl-json').read_text(encoding='utf-8'))
    assert csl['type'] == 'dataset'
    assert csl['title'] == expected['title']
    assert csl['DOI'] == expected['doi']
    assert csl['version'] == expected['version']
    assert csl['publisher'] == expected['publisher']
    assert csl['issued']['date-parts'] == [[int(expected['year'])]]
    assert csl['author'] == [{'given': expected['personGiven'], 'family': expected['personFamily']}, {'literal': expected['organization']}]
    print('Citation round-trip OK: actual BibTeX/RIS exports preserve version, DOI, structured/literal authors and escaped symbols; CSL-JSON agrees.')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('directory', nargs='?', type=Path, default=Path('build/citation-fixtures'))
    check(parser.parse_args().directory)
