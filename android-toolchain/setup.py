"""Download a pinned Linux Android toolchain; no credentials or elevated privileges."""
from pathlib import Path, PurePosixPath
import hashlib
import json
import os
import shutil
import tempfile
import urllib.request
import zipfile

BASE = Path(__file__).resolve().parent
LOCK = json.loads((BASE/'toolchain-lock.json').read_text())

def digest(path):
    h=hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda:stream.read(1024*1024),b''):h.update(block)
    return h.hexdigest()

def download(item):
    path=BASE/item['file']
    if not path.is_file() or digest(path)!=item['sha256']:
        temp=path.with_suffix(path.suffix+'.download')
        try:
            with urllib.request.urlopen(item['url'],timeout=60) as response,temp.open('wb') as out:
                total=0
                while True:
                    chunk=response.read(1024*1024)
                    if not chunk:break
                    total+=len(chunk)
                    if total>150*1024*1024:raise ValueError('Toolchain download exceeds limit')
                    out.write(chunk)
            if digest(temp)!=item['sha256']:raise ValueError('Checksum mismatch: '+item['file'])
            temp.replace(path)
        finally:
            temp.unlink(missing_ok=True)
    if path.suffix=='.zip':
        with zipfile.ZipFile(path) as archive:
            for entry in archive.infolist():
                rel=PurePosixPath(entry.filename)
                if rel.is_absolute() or '..' in rel.parts or '\\' in entry.filename:
                    raise ValueError('Unsafe toolchain archive path')
                if (entry.external_attr>>16)&0o170000 == 0o120000:
                    raise ValueError('Unexpected toolchain archive symlink')
                destination=BASE.joinpath(*rel.parts)
                if entry.is_dir():destination.mkdir(parents=True,exist_ok=True)
                else:
                    destination.parent.mkdir(parents=True,exist_ok=True)
                    with archive.open(entry) as source,destination.open('wb') as out:shutil.copyfileobj(source,out)
    print('Verified:',item['file'])

if __name__=='__main__':
    for item in LOCK['downloads']:download(item)
    for path in BASE.rglob('*'):
        if path.is_file() and path.name in {'aapt','aapt2','d8','apksigner','zipalign','dexdump'}:path.chmod(0o755)
