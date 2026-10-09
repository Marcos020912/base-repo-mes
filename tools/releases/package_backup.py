#!/usr/bin/env python3
"""Package an existing quiesced PostgreSQL dump, files and private configuration.
Never connects to a database, stops a service or restores data.
"""
import argparse
import hashlib
import io
import json
import os
from pathlib import Path
import stat
import subprocess
import tarfile
from datetime import datetime, timezone

def package(dump, files, config, output, writes_stopped=False, pg_restore='pg_restore'):
    if not writes_stopped:raise ValueError('Confirm writes stopped on every instance before packaging.')
    dump,files,config,output=map(Path,(dump,files,config,output))
    if not files.is_dir() or files.is_symlink():raise ValueError('Invalid data directory.')
    if output.resolve().is_relative_to(files.resolve()):raise ValueError('Backup must be outside the data tree.')
    entries=[(dump,'database.dump'),(config,'config/application.properties')]
    for path in sorted(files.rglob('*')):
        if path.is_symlink():raise ValueError('Symbolic links are not supported.')
        if path.is_dir():continue
        entries.append((path,'data/'+path.relative_to(files).as_posix()))
    for path,_ in entries:
        if path.is_symlink() or not path.is_file():raise ValueError('Only regular files are supported.')
    # Lists dump structure only. Never execute SQL or contact its original server.
    subprocess.run([pg_restore,'--list',str(dump)],check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,timeout=60)
    manifest={'schema':'reduniv.private-backup.v1','generatedAt':datetime.now(timezone.utc).isoformat(),'writesStoppedConfirmedByOperator':True,'files':[]}
    descriptor=os.open(output,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
    try:
        with os.fdopen(descriptor,'wb') as destination,tarfile.open(fileobj=destination,mode='w:gz') as archive:
            for path,name in entries:
                # Reject races replacing a validated regular file with a symlink.
                fd=os.open(path,os.O_RDONLY|os.O_NOFOLLOW)
                with os.fdopen(fd,'rb') as source:
                    before=os.fstat(source.fileno())
                    if not stat.S_ISREG(before.st_mode):raise ValueError('Source changed type.')
                    hasher=hashlib.sha256()
                    for chunk in iter(lambda: source.read(1024*1024), b''):
                        hasher.update(chunk)
                    digest=hasher.hexdigest();source.seek(0)
                    header=tarfile.TarInfo(name);header.size=before.st_size;header.mode=0o600;header.mtime=int(before.st_mtime)
                    archive.addfile(header,source);after=os.fstat(source.fileno())
                    if (before.st_size,before.st_mtime_ns)!=(after.st_size,after.st_mtime_ns):raise ValueError('Source changed during backup.')
                    manifest['files'].append({'path':name,'size':before.st_size,'sha256':digest})
            data=json.dumps(manifest,ensure_ascii=False,indent=2).encode();header=tarfile.TarInfo('manifest.json');header.size=len(data);header.mode=0o600;archive.addfile(header,io.BytesIO(data))
    except BaseException:
        output.unlink(missing_ok=True);raise
    return {'entries':len(entries),'manifest':'manifest.json','private':True}

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    for name in ('dump','files','config','output'):parser.add_argument('--'+name,required=True)
    parser.add_argument('--writes-stopped',action='store_true');parser.add_argument('--pg-restore',default='pg_restore');args=parser.parse_args()
    try:
        print(json.dumps(package(args.dump,args.files,args.config,args.output,args.writes_stopped,args.pg_restore)));return 0
    except (OSError,ValueError,subprocess.SubprocessError):
        print(json.dumps({'error':'Backup failed: confirm stopped writes, regular sources, valid dump and unused destination. No service or database was changed.'}));return 1
if __name__=='__main__':raise SystemExit(main())
