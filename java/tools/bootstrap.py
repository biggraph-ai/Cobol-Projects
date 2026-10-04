#!/usr/bin/env python3
"""Build the pinned Java runtime and converter without modifying COBOL sources."""
import hashlib,json,os,shutil,subprocess,sys
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
PREFIX=Path(os.environ.get('COBOL4J_HOME','/workspace/toolchains/cobol4j')).resolve()
SOURCE=PREFIX.parent/'opensourcecobol4j'
COMMIT='f46b43d1b50f47802f2d5c833f79202fb053d2ad'
JDK=Path(os.environ.get('JAVA_HOME','/workspace/toolchains/java/usr/lib/jvm/java-21-openjdk-amd64'))

def command(args,cwd=None):subprocess.run(list(map(str,args)),cwd=cwd,check=True)
def java_tool(name):
    path=JDK/'bin'/name
    return str(path) if path.exists() else shutil.which(name) or sys.exit('A JDK with '+name+' is required')

def main():
    PREFIX.mkdir(parents=True,exist_ok=True)
    if not SOURCE.exists():
        command(['git','clone','--no-checkout','--depth','1','--branch','v2.1.0','https://github.com/opensourcecobol/opensourcecobol4j.git',SOURCE])
        command(['git','checkout','--detach',COMMIT],SOURCE)
    actual=subprocess.check_output(['git','rev-parse','HEAD'],cwd=SOURCE,text=True).strip()
    if actual!=COMMIT:sys.exit('Compiler checkout is not the pinned commit; use a separate COBOL4J_HOME.')
    if subprocess.run(['git','diff','--quiet','HEAD','--'],cwd=SOURCE).returncode:
        sys.exit('Pinned dependency checkout has modified source; use a separate COBOL4J_HOME.')
    # Keep trust verification enabled: artifact bytes are pinned by SHA-256.
    dependencies=json.loads((ROOT/'java/tools/dependencies.lock.json').read_text())
    lib=PREFIX/'lib';lib.mkdir(exist_ok=True)
    for dep in dependencies:
        target=lib/dep['file']
        if not target.exists():command(['curl','--fail','--silent','--show-error','--location',dep['url'],'--output',target])
        if hashlib.sha256(target.read_bytes()).hexdigest()!=dep['sha256']:
            sys.exit('Dependency checksum mismatch: '+dep['file'])
    if '--compiler' in sys.argv:
        command(['./configure','--prefix='+str(PREFIX)],SOURCE)
        command(['make','defaults.h'],SOURCE)
        for folder in ('lib','cobj'):command(['make','-C',folder,'-j2'],SOURCE)
        for folder in ('config','copy','cobj'):command(['make','-C',folder,'install'],SOURCE)
    classes=PREFIX/'runtime-classes';classes.mkdir(exist_ok=True)
    sources=PREFIX/'runtime-sources.txt'
    sources.write_text('\n'.join(str(p) for p in sorted((SOURCE/'libcobj/app/src/main/java').rglob('*.java')))+'\n')
    command([java_tool('javac'),'-encoding','UTF-8','-cp',str(lib/'*'),'-d',classes,'@'+str(sources)])
    command([java_tool('jar'),'--create','--file',lib/'libcobj.jar','-C',classes,'.'])
    for name in ('COPYING','COPYING.LIB'):shutil.copy(SOURCE/name,PREFIX/name)
    print('Java runtime ready at',PREFIX)

if __name__=='__main__':main()
