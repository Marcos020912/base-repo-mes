#!/usr/bin/env python3
"""Read-only Git secret gate. Findings never contain values or secret fingerprints."""
import argparse
import json
import re
import subprocess
from pathlib import Path

PATTERNS = {
    'private-key': re.compile(r'-----BEGIN (?:RSA |EC |DSA |OPENSSH |ENCRYPTED )?PRIVATE KEY-----'),
    'github-token': re.compile(r'\b(?:gh[pousr]_[A-Za-z0-9]{36,}|github_pat_[A-Za-z0-9_]{40,})\b'),
    'aws-access-key': re.compile(r'\b(?:AKIA|ASIA)[A-Z0-9]{16}\b'),
}
PROPERTY = re.compile(r'^\s*([\w.-]*(?:password|secret|token|api[-_.]?key)[\w.-]*)\s*[:=]\s*(.*?)\s*$', re.I)

def inspect(data, filename):
    if b'\x00' in data:
        return []
    text = data.decode('utf-8', errors='replace')
    found = []
    properties = filename.endswith(('.properties', '.env')) and not filename.startswith('src/test/')
    for number, line in enumerate(text.splitlines(), 1):
        for rule, pattern in PATTERNS.items():
            if pattern.search(line):
                found.append({'path': filename, 'line': number, 'rule': rule})
        if properties:
            match = PROPERTY.match(line)
            if match:
                value = match[2].strip().strip('\"\'')
                # Empty or unresolved variable placeholders contain no checked-in credential.
                placeholder = re.fullmatch(r'\$\{[A-Z_a-z][\w.-]*(?::)?\}', value)
                documentation = re.fullmatch(r'<[A-Z0-9_ -]+>', value)
                if value and not placeholder and not documentation:
                    found.append({'path': filename, 'line': number, 'rule': 'literal-credential-property', 'key': match[1]})
    return found

def git(*args):
    return subprocess.check_output(['git', *args], stderr=subprocess.DEVNULL)

def audit(history=False):
    findings = []
    for name in git('ls-files', '-z').decode().split('\0'):
        if name and Path(name).is_file():
            findings.extend(inspect(Path(name).read_bytes(), name))
    if history:
        seen = set()
        for entry in git('rev-list', '--objects', '--all').decode().splitlines():
            parts = entry.split(' ', 1)
            if len(parts) != 2:
                continue
            oid, filename = parts
            if oid in seen:
                continue
            seen.add(oid)
            if git('cat-file', '-t', oid).strip() != b'blob':
                continue
            for finding in inspect(git('cat-file', 'blob', oid), filename):
                finding['scope'] = 'history'
                finding['object'] = oid  # Git object, never a hash of an individual password.
                findings.append(finding)
    return findings

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--history', action='store_true', help='Also inspect unique blobs reachable from all local refs')
    args = parser.parse_args()
    findings = audit(args.history)
    print(json.dumps({'findings': findings, 'count': len(findings), 'history': args.history,
                      'limitations': 'Heuristic gate, not proof that arbitrary secrets are absent.'}, ensure_ascii=False, indent=2))
    return 1 if findings else 0

if __name__ == '__main__':
    raise SystemExit(main())
