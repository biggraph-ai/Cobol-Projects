#!/usr/bin/env python3
"""Compare Java batch behavior with GnuCOBOL in isolated fixture trees."""
import importlib.util,json,os,re,shutil,subprocess,tempfile,sys,difflib
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
BUILD=Path(os.environ.get('JAVA_BUILD_DIR','/workspace/cobol-java-build'))
RUNTIME=Path(os.environ.get('COBOL4J_HOME','/workspace/toolchains/cobol4j'))
spec=importlib.util.spec_from_file_location('conversion',ROOT/'java/tools/generate.py')
conversion=importlib.util.module_from_spec(spec);spec.loader.exec_module(conversion)
ROWS=json.loads((ROOT/'java/conversion.json').read_text())
RESULTS=[]

def run(args,cwd,stdin=''):
    return subprocess.run(args,cwd=cwd,env=os.environ, input=stdin, text=True,capture_output=True,timeout=10)

def snapshot(base):
    tree=base/'repo'
    for folder in ('common','OpenCobol'):
        shutil.copytree(ROOT/folder,tree/folder)
    for folder in ('prod','ECBAP','dastagg','Internal-Sort'):
        local=tree/'OpenCobol'/folder
        (local/'jcl').mkdir(exist_ok=True); (local/'spool').mkdir(exist_ok=True);(local/'data').mkdir(exist_ok=True)
        shutil.copy(ROOT/'common/data/customer.dat.txt',local/'data/customer.dat.txt')
        shutil.copy(ROOT/'common/data/c08-members.dat.txt',local/'data/members.dat')
    return tree

def bindings(tree, source, outcome):
    """Prepare explicit fixed-record test datasets, never mutate repository data."""
    family=Path(source).parent.parent
    spool=tree/'OpenCobol'/family.name/'spool'
    def fixed(name, fixture, width):
        lines=(tree/'common/data'/fixture).read_bytes().splitlines()
        target=spool/(name+'.dat')
        target.write_bytes(b''.join(row.ljust(width,b' ')[:width] for row in lines))
        os.environ['DD_'+name]=str(target)
        os.environ['DD_'+name.rsplit('-',1)[-1]]=str(target)
    if source.endswith('/FAVRPT2.cbl'):
        fixed('DA-S-FAVIN','ECBAP/favin.dat.txt',58)
        os.environ['DD_DA-S-FAVOUT']=str(spool/'favin-output.dat')
        os.environ['DD_FAVOUT']=str(spool/'favin-output.dat')
        outcome['fixture_preparation']='58-byte fixed records from the repository favin fixture; explicit DD bindings'
    if source.endswith('/TABLES00.cbl'):
        fixed('UT-S-EMPFILE','ECBAP/empfile.dat.txt',80)
        os.environ['DD_UT-S-PHONEOUT']=str(spool/'phone-output.dat')
        os.environ['DD_PHONEOUT']=str(spool/'phone-output.dat')
        outcome['fixture_preparation']='80-byte fixed employee records with explicit DD bindings'
    if source.endswith('/TABLES02.cbl'):
        fixed('UT-S-STDNTCRS','ECBAP/student-cours.dat.txt',80)
        os.environ['DD_UT-S-STCRSRPT']=str(spool/'student-output.dat')
        os.environ['DD_STCRSRPT']=str(spool/'student-output.dat')
        outcome['fixture_preparation']='80-byte fixed student course records with explicit DD bindings'
    if source.endswith('/TABLESA1.cbl'):
        fixed('EMPROJ','ECBAP/emp.proj.dat.txt',80)
        outcome['fixture_preparation']='80-byte fixed employee project records with explicit DD binding'
    if source=='OpenCobol/dastagg/cbl/HOSPEDIT.cbl':
        fixed('HOSPIN','ECBAP/hospin.dat.txt',100)
        for name in ('HOSPOUT','RPTFILE','ERRFILE'):
            os.environ['DD_'+name]=str(spool/(name.lower()+'.dat'))
        outcome['fixture_preparation']='100-byte fixed hospital records with explicit input and output DD bindings'
    if Path(source).name in ('CBL0001.cbl','CBL0002.cbl','ODS0002.cbl'):
        record=b'00000001'+bytes.fromhex('000001000C000000500C')
        for value,width in [('Lovelace',20),('Ada',15),('Main Street',25),('London',20),('NY',15),('',7),('Fixture account',50)]:
            record+=value.encode().ljust(width,b' ')
        assert len(record)==170
        target=spool/'accounts.dat';target.write_bytes(record)
        os.environ['DD_ACCTREC']=str(target)
        os.environ['DD_PRTLINE']=str(spool/'accounts-output.dat')
        outcome['fixture_preparation']='Synthetic 170-byte account record with valid signed packed-decimal balances'
    if source.endswith('/TABLES01.cbl'):
        file=tree/'common/data/ECBAP/emp1.proj.dat.txt'
        rows=file.read_bytes().splitlines()
        # Original line 13 is short and contains SQL characters in a numeric rate.
        # Use a clearly identified valid fixture for arithmetic parity.
        rows[12]=rows[12][:55]+b'00'+rows[12][55:]
        file.write_bytes(b'\n'.join(rows)+b'\n')
        outcome['fixture_preparation']='Prepared valid fixture: insert two missing numeric positions in original employee-project line 13; original data remains unchanged'

def main():
    requested=set(sys.argv[1:])
    if requested:
        RESULTS.extend(r for r in json.loads((ROOT/'java/validation.json').read_text()) if r['source'] not in requested)
    for row in ROWS:
        if row['status']!='generated':continue
        if requested and row['source'] not in requested:continue
        source=ROOT/row['source']; text=source.read_text(errors='replace')
        clean='\n'.join(l[7:72] for l in text.splitlines() if len(l)>6 and l[6] not in '*/')
        outcome={'source':row['source'],'status':'unrun'}
        if re.search(r'PROCEDURE\s+DIVISION\s+USING',clean,re.I):
            outcome['reason']='Subprogram; exercised through its caller where available'
            RESULTS.append(outcome);continue
        if re.search(r'\bSELECT\b',clean,re.I) and not row['source'].startswith('OpenCobol/'):
            outcome['reason']='Mainframe dataset bindings required'
            RESULTS.append(outcome);continue
        if re.search(r'CURRENT-DATE|FUNCTION\s+RANDOM',clean,re.I):
            outcome['reason']='Time-dependent or random demonstration; compilation checked'
            RESULTS.append(outcome);continue
        # Only run file programs whose fixture paths can be resolved.
        if row['source'].startswith('OpenCobol/'):
            missing=[]
            for path in re.findall(r'ASSIGN\s+TO\s+[\'"]([^\'"]+)[\'"]',clean,re.I):
                if 'spool' in path:continue
                resolved=(source.parent.parent/'jcl'/path).resolve()
                if not resolved.exists() and Path(path).name not in ('customer.dat.txt','members.dat'):
                    missing.append(path)
            if missing:
                outcome['reason']='Missing repository input fixtures: '+', '.join(missing)
                RESULTS.append(outcome);continue
        try:
            with tempfile.TemporaryDirectory(prefix='java-parity-',dir='/workspace') as tmp:
                base=Path(tmp); native_tree=snapshot(base/'native');java_tree=snapshot(base/'java')
                executable=base/'reference'
                includes=[source.parent.parent/'cpy',source.parent.parent/'COPYBOOK',ROOT/'common/cpy']
                dialect='ibm' if row['source'].startswith('Mainframe/') else 'default'
                args=['cobc','-x','-std='+dialect,'-ffold-copy=UPPER']
                for inc in includes:
                    if inc.is_dir():args+=['-I',str(inc)]
                extras=[]
                if source.name=='MAINPGM.cbl': extras=list(source.parent.glob('SUB0*.cbl'))
                compile=run(args+[str(source),*map(str,extras),'-o',str(executable)],base)
                outcome['reference']='original COBOL'
                if compile.returncode:
                    adapted,changes=conversion.adapt(text,row['source'])
                    temporary=base/source.name;temporary.write_text(adapted)
                    compile=run(args+['-ftext-column=250','-fno-areacheck']+[str(temporary),*map(str,extras),'-o',str(executable)],base)
                    outcome['reference']='adapted COBOL';outcome['adaptations']=changes
                if compile.returncode:
                    outcome.update(status='reference_compile_failed',reason=compile.stderr[-1500:])
                    RESULTS.append(outcome);continue
                packages=[r['package'] for r in ROWS if Path(r['source']).parent==source.relative_to(ROOT).parent and r['status']=='generated']
                os.environ['COB_PACKAGE_PATH']=os.pathsep.join(packages)
                if row['source'].startswith('OpenCobol/'):
                    rel=source.relative_to(ROOT).parent.parent/'jcl'
                    ncwd=native_tree/rel; jcwd=java_tree/rel
                else:ncwd=native_tree;jcwd=java_tree
                stdin='../../../common/data/c08-longnames.dat.txt\n' if source.name=='BDS0804.cbl' else ''
                if source.name=='ADDAMT.cbl':stdin='Ada\n00010\n00020\n00030\nNO\n'
                bindings(native_tree,row['source'],outcome)
                native=run([str(executable)],ncwd,stdin)
                bindings(java_tree,row['source'],outcome)
                java=run(['java','-Xmx512m','-cp',str(BUILD/'classes')+os.pathsep+str(RUNTIME/'lib/*'),row['classes'][0]],jcwd,stdin)
                files_native={str(p.relative_to(native_tree)):p.read_bytes() for p in native_tree.rglob('*') if p.is_file()}
                files_java={str(p.relative_to(java_tree)):p.read_bytes() for p in java_tree.rglob('*') if p.is_file()}
                different=[p for p in files_native.keys()|files_java.keys() if files_native.get(p)!=files_java.get(p)]
                outcome['exit_native']=native.returncode;outcome['exit_java']=java.returncode
                if native.returncode or java.returncode:
                    outcome.update(status='execution_failed',reason=(native.stderr+'\n'+java.stderr)[-1500:])
                elif native.stdout!=java.stdout or different:
                    outcome.update(status='mismatch',different_files=different,
                                   native_stdout=native.stdout[:12000],java_stdout=java.stdout[:12000],
                                   stdout_difference='\n'.join(difflib.unified_diff(native.stdout.splitlines(),java.stdout.splitlines()))[:5000],
                                   file_differences={p:{'native':repr(files_native.get(p,b'')[-1800:]),'java':repr(files_java.get(p,b'')[-1800:])} for p in different})
                else: outcome['status']='passed_prepared_fixture' if 'fixture_preparation' in outcome else 'passed'
        except subprocess.TimeoutExpired:
            outcome.update(status='execution_failed',reason='10-second execution timeout')
        RESULTS.append(outcome)
        print(outcome['status'],row['source'],flush=True)
    from collections import Counter
    (ROOT/'java/validation.json').write_text(json.dumps(RESULTS,indent=2)+'\n')
    print(dict(Counter(r['status'] for r in RESULTS)))
    return int(any(r['status'] in ('mismatch','execution_failed','reference_compile_failed') for r in RESULTS))

if __name__=='__main__':raise SystemExit(main())
