import csv
import io
from pathlib import Path
import stat
import tempfile
import unittest

from audit_content_paths import audit, classify, file_uri_path


class ContentPathAuditTest(unittest.TestCase):
    def test_classifies_local_missing_external_symlink_and_remote_uris(self):
        with tempfile.TemporaryDirectory() as directory:
            home = Path(directory)
            root = home / "repository"
            root.mkdir()
            internal = root / "datos uno.csv"
            internal.write_text("a,b\n1,2\n")
            external = home / "privado.txt"
            external.write_text("no disponible")
            (root / "salida.txt").symlink_to(external)
            root = root.resolve()

            self.assertEqual("OK", classify(internal.as_uri(), root)[0])
            self.assertEqual("MISSING_FILE", classify((root / "falta.csv").as_uri(), root)[0])
            self.assertEqual("OUTSIDE_ROOT", classify(external.as_uri(), root)[0])
            self.assertEqual("OUTSIDE_ROOT", classify((root / "salida.txt").as_uri(), root)[0])
            self.assertEqual("UNSUPPORTED_URI", classify("https://example.org/data.csv", root)[0])
            self.assertEqual("MISSING_URI", classify(None, root)[0])
            self.assertIsNone(file_uri_path("file:/tmp/archivo%ZZ.txt"))
            self.assertIsNone(file_uri_path("file:/tmp/archivo.txt?download=true"))
            self.assertIsNone(file_uri_path("file:/tmp/archivo%00.txt"))

    def test_streams_csv_and_writes_private_anomaly_report(self):
        with tempfile.TemporaryDirectory() as directory:
            home = Path(directory)
            root = home / "repository"
            root.mkdir()
            good = root / "good.csv"
            good.write_text("a\n1\n")
            report = home / "anomalies.csv"
            source = io.StringIO()
            writer = csv.writer(source)
            writer.writerow(["id", "parent_resource_id", "relative_path", "content_uri"])
            writer.writerow([1, "dataset-1", "good.csv", good.as_uri()])
            writer.writerow([2, "dataset-1", "missing.csv", (root / "missing.csv").as_uri()])
            source.seek(0)
            counts, total = audit(source, root.resolve(), report)
            self.assertEqual(2, total)
            self.assertEqual({"OK": 1, "MISSING_FILE": 1}, counts)
            self.assertEqual(0o600, stat.S_IMODE(report.stat().st_mode))
            with report.open(encoding="utf-8", newline="") as stream:
                rows = list(csv.DictReader(stream))
            self.assertEqual(1, len(rows))
            self.assertEqual("2", rows[0]["id"])
            self.assertEqual("MISSING_FILE", rows[0]["status"])


if __name__ == "__main__":
    unittest.main()
