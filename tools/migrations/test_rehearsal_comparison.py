import pathlib
import subprocess
import tempfile
import unittest

SCRIPT = pathlib.Path(__file__).with_name('rehearse.sh').read_text()
NORMALIZE = SCRIPT.split('normalize_dump() {', 1)[1].split('\ndump_for_comparison()', 1)[0]
NORMALIZE = 'normalize_dump() {' + NORMALIZE

class ComparisonTest(unittest.TestCase):
    def normalize(self, sql):
        return subprocess.run(['bash', '-c', NORMALIZE + '\nnormalize_dump'],
                              input=sql, text=True, capture_output=True, check=True).stdout

    def test_only_random_restriction_keys_are_normalized(self):
        a = '\\restrict AAA123\nCREATE TABLE example(id bigint);\n\\unrestrict AAA123\n'
        b = a.replace('AAA123', 'BBB789')
        self.assertEqual(self.normalize(a), self.normalize(b))

    def test_real_schema_differences_still_detected(self):
        a = 'CREATE TABLE example(id bigint);\n'
        b = 'CREATE TABLE example(id integer);\n'
        self.assertNotEqual(self.normalize(a), self.normalize(b))

    def test_data_and_sql_literals_are_not_rewritten(self):
        sql = "INSERT INTO example VALUES ('AAA123');\n-- \\restrict AAA123\n"
        self.assertEqual(sql, self.normalize(sql))

    def test_private_difference_survives_failure(self):
        function = 'compare_dump() {' + SCRIPT.split('compare_dump() {', 1)[1].split('\nTEMP=', 1)[0]
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            (root/'a.sql').write_text('CREATE TABLE a(id bigint);\n')
            (root/'b.sql').write_text('CREATE TABLE a(id integer);\n')
            (root/'test.log').write_text('fixture\n')
            (root/'diagnostics').mkdir(mode=0o700)
            command = function + '\ncompare_dump "$TEMP/a.sql" "$TEMP/b.sql" failure'
            result = subprocess.run(['bash','-c',command], env={'PATH':'/usr/bin:/bin', 'TEMP':directory,
                                   'DIAGNOSTICS':str(root/'diagnostics')}, capture_output=True)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn('CREATE TABLE', (root/'diagnostics/difference.diff').read_text())

if __name__ == '__main__': unittest.main()
