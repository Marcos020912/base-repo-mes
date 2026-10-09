#!/usr/bin/env python3
"""Verify an operator-supplied release checksum and audit application-owned JAR entries.
Does not execute code, extract files, contact GitHub or print secret values.
"""
import argparse
import hashlib
import hmac
import importlib.util
import json
from pathlib import Path, PurePosixPath
import re
import zipfile
spec=importlib.util.spec_from_file_location('secret_audit',Path(__file__).resolve().parents[1]/'security/audit_secrets.py')
audit=importlib.util.module_from_spec(spec);spec.loader.exec_module(audit)

def verify(filename, expected):
    if not re.fullmatch(r'[a-fA-F0-9]{64}',expected):
        raise ValueError('SHA-256 esperado inválido; obténgalo de la Release aprobada.')
    with open(filename,'rb') as stream:
        digest=hashlib.file_digest(stream,'sha256').hexdigest()
    if not hmac.compare_digest(digest,expected.lower()):
        raise ValueError('El SHA-256 no coincide; no iniciar ni instalar este JAR.')
    findings=[]; scanned=0
    with zipfile.ZipFile(filename) as jar:
        names=jar.namelist()
        if len(names)!=len(set(names)) or any(n.startswith('/') or '..' in PurePosixPath(n).parts for n in names):
            raise ValueError('El JAR contiene rutas ambiguas o duplicadas.')
        if 'META-INF/MANIFEST.MF' not in names or not any(n.startswith('BOOT-INF/classes/') for n in names):
            raise ValueError('No es un bootJar.')
        for info in jar.infolist():
            if info.is_dir() or not (info.filename.startswith('BOOT-INF/classes/') or info.filename.startswith('META-INF/')):
                continue
            if info.file_size>8*1024*1024:
                raise ValueError('Entrada propia demasiado grande para auditar con seguridad.')
            data=jar.read(info);scanned+=1
            findings.extend(audit.inspect(data,info.filename))
            # Binary classes can embed known token/key patterns; do not skip those signatures.
            if b'\0' in data:
                for name,pattern in audit.PATTERNS.items():
                    if pattern.search(data.decode('utf-8',errors='replace')):
                        findings.append({'path':info.filename,'rule':name})
            if PurePosixPath(info.filename).name=='application.properties':
                findings.append({'path':info.filename,'rule':'embedded-deployment-configuration'})
    return {'sha256':digest,'scannedApplicationEntries':scanned,'findings':findings,'count':len(findings),
            'limitations':'Checksum verifies supplied digest, not publisher identity or Git correspondence. Dependencies and unknown secret patterns require separate review.'}

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('jar');parser.add_argument('sha256');args=parser.parse_args()
    try:
        result=verify(args.jar,args.sha256);print(json.dumps(result,ensure_ascii=False,indent=2));return 1 if result['count'] else 0
    except (OSError,ValueError,zipfile.BadZipFile):
        print(json.dumps({'error':'Artifact checksum, format or safety verification failed. No service was changed.'}));return 1
if __name__=='__main__':raise SystemExit(main())
