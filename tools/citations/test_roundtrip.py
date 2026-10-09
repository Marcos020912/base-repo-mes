import contextlib
import io
from pathlib import Path
import shutil
import tempfile
import unittest
from check_roundtrip import check


class ExportRegressionTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.directory = Path(self.temp.name)
        source = Path('build/citation-fixtures')
        if not source.is_dir():
            self.fail('Generate fixtures with ScientificCitationControllerTest first.')
        for file in source.iterdir():
            shutil.copyfile(file, self.directory / file.name)

    def tearDown(self):
        self.temp.cleanup()

    def run_check(self):
        with contextlib.redirect_stdout(io.StringIO()):
            check(self.directory)

    def test_actual_exports_pass(self):
        self.run_check()

    def test_wrong_ris_version_is_rejected(self):
        file = self.directory / 'citation.ris'
        file.write_text(file.read_text().replace('ET  - 2.0', 'ET  - 1.0'))
        with self.assertRaises(AssertionError):
            self.run_check()

    def test_split_institutional_bibtex_author_is_rejected(self):
        file = self.directory / 'citation.bibtex'
        file.write_text(file.read_text().replace('{Research and Development}', 'Research and Development'))
        with self.assertRaises(AssertionError):
            self.run_check()

    def test_wrong_csl_doi_is_rejected(self):
        file = self.directory / 'citation.csl-json'
        file.write_text(file.read_text().replace('10.1234/exact-v2', '10.1234/wrong-version'))
        with self.assertRaises(AssertionError):
            self.run_check()


if __name__ == '__main__':
    unittest.main()
