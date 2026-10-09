#!/usr/bin/env python3
"""Stop only Java -jar processes whose resolved JAR belongs to this installation."""
import argparse,os,signal,time
from pathlib import Path

def find_pids(root, proc=Path('/proc')):
    root=Path(root).resolve();allowed={root/'build/libs/base-repo.jar',root/'build/libs/base_repo.jar'};result=[]
    for folder in proc.glob('[0-9]*'):
        try:
            args=(folder/'cmdline').read_bytes().split(b'\0')
            if b'-jar' not in args:continue
            jar=Path(os.fsdecode(args[args.index(b'-jar')+1]));cwd=(folder/'cwd').resolve()
            if (cwd/jar).resolve() in allowed:result.append(int(folder.name))
        except (OSError,IndexError):continue
    return result

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--root',required=True);args=parser.parse_args()
    for pid in find_pids(args.root):
        os.kill(pid,signal.SIGTERM)
        for _ in range(300):
            try:os.kill(pid,0)
            except ProcessLookupError:break
            time.sleep(.2)
        else:raise SystemExit('Application did not stop; aborting without force kill')
if __name__=='__main__':main()
