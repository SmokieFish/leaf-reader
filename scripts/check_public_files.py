#!/usr/bin/env python3
"""Conservative release gate for tracked files and every reachable Git revision."""
from pathlib import Path
import hashlib
import re
import subprocess
import sys
import zipfile
import io

ROOT=Path(__file__).resolve().parents[1]
SECRET_PATTERNS=[
 re.compile(rb'-----BEGIN (?:RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----'),
 re.compile(rb'\b(?:sk-[A-Za-z0-9_-]{20,}|gh[porus]_[A-Za-z0-9_]{20,}|github_pat_[A-Za-z0-9_]{20,}|AKIA[A-Z0-9]{16})\b'),
]
FORBIDDEN_SUFFIXES={'.p12','.pfx','.jks','.keystore','.key','.pem','.epub','.db','.sqlite','.sqlite3','.apk'}
TEST_JAR_SHA='3cf6cd6892e32e2b4c1c39e0f52f5248a2f5b37646fdfbb79a66b46b618414ed'

def check(path,data):
 issues=[];name=Path(path)
 if name.suffix.lower() in FORBIDDEN_SUFFIXES or name.name=='.env' or name.name.startswith('.env.'):
  issues.append('private/generated/user file type')
 if '/build/' in '/'+path or '/signing/' in '/'+path or '/upload/' in '/'+path:
  issues.append('build, signing or personal-upload directory')
 if any(pattern.search(data) for pattern in SECRET_PATTERNS):issues.append('credential/private-key pattern')
 try:data.decode('utf-8')
 except UnicodeDecodeError:
  if path!='leaf-android/tests/lib/json-20240303.jar' or hashlib.sha256(data).hexdigest()!=TEST_JAR_SHA:
   issues.append('unreviewed binary file')
 return issues

def git(*args):return subprocess.check_output(['git','-C',str(ROOT),*args])

def main():
 failures=[];blobs={}
 # Include uncommitted tracked files when run locally before creating a release.
 for raw in git('ls-files','-z').split(b'\0'):
  if not raw:continue
  path=raw.decode();file=ROOT/path
  if file.is_file():
   for issue in check(path,file.read_bytes()):failures.append((path,issue))
 for row in git('rev-list','--objects','--all').decode().splitlines():
  parts=row.split(' ',1)
  if len(parts)!=2:continue
  oid,path=parts
  if git('cat-file','-t',oid).strip()!=b'blob':continue
  blobs[oid]=path
 for oid,path in blobs.items():
  for issue in check(path,git('cat-file','blob',oid)):failures.append((path,issue+' in history'))
 if failures:
  for path,issue in sorted(set(failures)):print('FAIL:',path,'—',issue)
  raise SystemExit(1)
 print('PASS: tracked files and',len(blobs),'historical blobs passed publication checks.')
 print('This is a pattern/file-integrity check, not a guarantee of absence of vulnerabilities.')

if __name__=='__main__':main()
