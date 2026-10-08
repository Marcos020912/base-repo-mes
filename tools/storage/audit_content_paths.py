#!/usr/bin/env python3
"""Read-only audit of ContentInformation file: URIs exported as CSV."""

import argparse
import csv
import os
from pathlib import Path
import sys
from urllib.parse import unquote_to_bytes, urlsplit


def file_uri_path(value: str) -> Path | None:
    try:
        if any(character.isspace() or ord(character) < 0x20 for character in value):
            return None
        for index, character in enumerate(value):
            if character == "%" and (index + 2 >= len(value) or
                                     any(digit not in "0123456789abcdefABCDEF" for digit in value[index + 1:index + 3])):
                return None
        uri = urlsplit(value)
        if uri.scheme.lower() != "file" or uri.netloc or uri.query or uri.fragment or not uri.path.startswith("/"):
            return None
        decoded = unquote_to_bytes(uri.path).decode("utf-8")
        if any(ord(character) < 0x20 or ord(character) == 0x7f for character in decoded):
            return None
        return Path(decoded)
    except (TypeError, UnicodeError, ValueError):
        return None


def classify(location: str | None, root: Path) -> tuple[str, str]:
    if not location:
        return "MISSING_URI", "No hay contentUri."
    candidate = file_uri_path(location)
    if candidate is None:
        return "UNSUPPORTED_URI", "No es una URL file: local absoluta válida."
    try:
        real = candidate.resolve(strict=True)
    except (OSError, RuntimeError, ValueError):
        return "MISSING_FILE", "El destino no existe o no puede resolverse."
    if not real.is_relative_to(root):
        return "OUTSIDE_ROOT", "La ruta real o un enlace simbólico sale de repo.basepath."
    if not real.is_file():
        return "NOT_FILE", "El destino no es un archivo regular."
    if not os.access(real, os.R_OK):
        return "UNREADABLE", "El archivo no puede leerse con esta cuenta."
    return "OK", ""


def audit(source, root: Path, report_path: Path | None) -> tuple[dict[str, int], int]:
    reader = csv.DictReader(source)
    if not reader.fieldnames:
        raise ValueError("El CSV está vacío o no tiene cabecera.")
    columns = {name.lower().replace("_", ""): name for name in reader.fieldnames}
    uri_column = columns.get("contenturi")
    if not uri_column:
        raise ValueError("Falta la columna content_uri (o contentUri).")
    count: dict[str, int] = {}
    total = 0
    report = None
    try:
        if report_path:
            fd = os.open(report_path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
            os.fchmod(fd, 0o600)
            report = os.fdopen(fd, "w", encoding="utf-8", newline="")
            writer = csv.writer(report)
            writer.writerow(["id", "parent_resource_id", "relative_path", "content_uri", "status", "reason"])
        for row in reader:
            total += 1
            status, reason = classify(row.get(uri_column), root)
            count[status] = count.get(status, 0) + 1
            if report and status != "OK":
                writer.writerow([row.get(columns.get("id", ""), ""),
                                 row.get(columns.get("parentresourceid", ""), ""),
                                 row.get(columns.get("relativepath", ""), ""),
                                 row.get(uri_column, ""), status, reason])
    finally:
        if report:
            report.close()
    return count, total


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--basepath", required=True, help="Valor file: de repo.basepath, o ruta absoluta local")
    parser.add_argument("--csv", default="-", help="Exportación CSV; '-' lee stdin")
    parser.add_argument("--report", type=Path, help="CSV de anomalías con rutas (se crea con permisos 0600)")
    parser.add_argument("--strict", action="store_true", help="Devolver código 1 si aparece alguna anomalía")
    args = parser.parse_args()
    root = file_uri_path(args.basepath) if args.basepath.startswith("file:") else Path(args.basepath)
    if root is None or not root.is_absolute():
        parser.error("--basepath debe ser una ruta absoluta o una URL file: local.")
    try:
        root = root.resolve(strict=True)
        if not root.is_dir():
            parser.error("--basepath no señala una carpeta.")
        if args.csv == "-":
            counts, total = audit(sys.stdin, root, args.report)
        else:
            with open(args.csv, encoding="utf-8", newline="") as source:
                counts, total = audit(source, root, args.report)
    except (OSError, ValueError) as error:
        parser.error(str(error))
    print(f"Registros: {total}. " + ", ".join(f"{key}: {value}" for key, value in sorted(counts.items())))
    if args.report:
        print(f"Anomalías detalladas: {args.report} (contiene rutas internas; protéjalo y elimínelo tras su uso).")
    return 1 if args.strict and total != counts.get("OK", 0) else 0


if __name__ == "__main__":
    raise SystemExit(main())
