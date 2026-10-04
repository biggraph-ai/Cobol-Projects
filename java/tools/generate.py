#!/usr/bin/env python3
"""Generate Java sources with a pinned, real COBOL-to-Java compiler."""
import csv
import hashlib
import itertools
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[2]
COMPILER = Path(os.environ.get('COBJ', '/workspace/toolchains/cobol4j/bin/cobj'))
OUT = ROOT / 'java/generated'
REPORT = ROOT / 'java/conversion.json'

def adapt(text, source):
    """Apply explicit compiler compatibility changes to a temporary copy only."""
    changes = []
    lines = []
    for line in text.splitlines():
        if len(line) > 6 and line[6] in '*/':
            lines.append(line[:72]); continue
        if len(line) > 6 and line[6] not in ' -Dd' and line[:6].isspace():
            line = ' ' + line
            changes.append('Repair invalid fixed-format level-number indicator')
        lines.append(line[:72])
    text = '\n'.join(lines) + '\n'
    # This compiler requires the PROGRAM-ID sentence period before AUTHOR.
    text, count = re.subn(r'(PROGRAM-ID\.\s*[\w-]+)(\s*\n)', r'\1.\2', text, flags=re.I)
    if count: changes.append('Terminate PROGRAM-ID sentence')
    text, count = re.subn(r'(PROGRAM-ID\.)\s*MAIN\.', r'\1 MAINPGM.', text, flags=re.I)
    if count: changes.append('Rename MAIN to MAINPGM: MAIN is reserved by the Java generator')
    text, count = re.subn(r'(PIC\s+[X9]\([^)]*\)\s+)VALUES\b', r'\1VALUE', text, flags=re.I)
    if count: changes.append('Use singular VALUE for elementary initial values')
    if source == 'pgm_templates/Batch/slt_base_ibm_batch.cob':
        text = text.replace('END PROGRAM base_ibm.', 'END PROGRAM PGM-NAME.')
        changes.append('Make template END PROGRAM match PROGRAM-ID')
    if source == 'Mainframe/ZOS/Normal/cbl/FAVRPT.cbl':
        text = text.replace('WRITE FAVOUT-REC.', 'WRITE FAVOUT-RECORD.')
        text = text.replace('PERFORM 5010-Move-Favin-Values UNTIL WS-Favin-EOF\n',
                            'PERFORM 5010-Move-Favin-Values UNTIL WS-Favin-EOF.\n')
        at = text.index('FD  FAVOUT')
        text = text[:at] + text[at:].replace('RECORD CONTAINS 58 CHARACTERS.', 'RECORD CONTAINS 63 CHARACTERS.', 1)
        changes.append('Repair undefined FAVOUT-REC, missing sentence period and inconsistent output record length')
    if source == 'OpenCobol/ECBAP/cbl/UNSTRNG.cbl':
        at = text.index('01  MISC-FIELDS.') + len('01  MISC-FIELDS.')
        text = text[:at] + text[at:].replace('01  MISC-FIELDS.', '01  MISC-FIELDS-2.', 1)
        changes.append('Give duplicate unreferenced MISC-FIELDS group a unique Java storage name')
    # ALL in an intrinsic function is expanded to the declared occurrences.
    # Explicit indices preserve order, multidimensional selection and repeated keys.
    data = text.split('PROCEDURE DIVISION', 1)[0]
    declarations = list(re.finditer(r'^.{7}\s*(\d{2})\s+([\w-]+)\b', data, re.M))
    stack = []; dimensions = {}
    for i, match in enumerate(declarations):
        level = int(match[1]); name = match[2].upper()
        if level == 88: continue
        while stack and stack[-1][0] >= level: stack.pop()
        clause = data[match.end():declarations[i+1].start() if i+1<len(declarations) else len(data)]
        occurs = re.search(r'\bOCCURS\s+(\d+)(?:\s+TO\s+(\d+))?', clause, re.I)
        own = [int(occurs[2] or occurs[1])] if occurs else []
        dims = (stack[-1][1] if stack else []) + own
        dimensions[name] = dims
        stack.append((level, dims))
    pattern = r'([A-Za-z][\w-]*)\s*\(([^()]*\bALL\b[^()]*)\)'
    def expand(match):
        name = match[1]; args = [a.strip() for a in match[2].replace('\n', ' ').split(',')]
        # Continuation lines have seven leading spaces; removing newlines suffices.
        dims = dimensions.get(name.upper(), [])
        if len(args) != len(dims):
            return match[0]
        options = [list(map(str, range(1, dims[i]+1))) if a.upper()=='ALL' else [a] for i,a in enumerate(args)]
        changes.append('Expand '+name+' ALL subscripts to explicit occurrence arguments')
        return ', '.join(name+'('+', '.join(indices)+')' for indices in itertools.product(*options))
    text = re.sub(pattern, expand, text, flags=re.I)
    folded = []
    for line in text.splitlines():
        if len(line) <= 180:
            folded.append(line); continue
        tokens = re.findall(r'"(?:[^"]|"")*"|\'(?:[^\']|\'\')*\'|\S+', line[7:])
        chunk = ''
        for token in tokens:
            if chunk and len(chunk)+len(token)+1 > 65:
                folded.append('           '+chunk); chunk = ''
            chunk += (' ' if chunk else '') + token
        if chunk: folded.append('           '+chunk)
    text = '\n'.join(folded)+'\n'
    return text, list(dict.fromkeys(changes))

def package_for(path):
    return 'org.biggraph.generated.' + '.'.join(
        'p_' + re.sub(r'[^a-z0-9_]', '_', part.lower())
        for part in path.with_suffix('').parts)

def main():
    if not COMPILER.is_file():
        sys.exit('Install the compiler using python3 java/tools/bootstrap.py --compiler first.')
    results = []
    for path in sorted(ROOT.rglob('*')):
        if path.suffix.lower() not in ('.cbl', '.cob') or 'java' in path.relative_to(ROOT).parts:
            continue
        relative = path.relative_to(ROOT)
        source = path.read_bytes()
        text = source.decode('utf-8', errors='replace')
        package = package_for(relative)
        target = OUT.joinpath(*package.split('.'))
        target.mkdir(parents=True, exist_ok=True)
        for old in target.glob('*.java'): old.unlink()
        row = {'source': relative.as_posix(), 'sha256': hashlib.sha256(source).hexdigest(),
               'package': package, 'classes': [], 'status': 'pending', 'diagnostics': ''}
        row['program_type']='subprogram' if re.search(r'PROCEDURE\s+DIVISION\s+USING',text,re.I) else 'program'
        if not source.strip():
            row['status'] = 'empty_source'
        else:
            includes = [path.parent.parent/'cpy', path.parent.parent/'COPYBOOK', ROOT/'common/cpy']
            adapted, row['adaptations'] = adapt(text, relative.as_posix())
            dialect = 'ibm' if relative.parts[0]=='Mainframe' else 'default'
            args = [str(COMPILER), '-C', '-variable', '-std='+dialect, '-ffold-copy-upper', '-java-package='+package]
            if relative.as_posix() != 'OpenCobol/prod/cbl/BDS0804.cbl':
                args.append('-assign_external')
            for inc in includes:
                if inc.is_dir(): args += ['-I', str(inc)]
            with tempfile.TemporaryDirectory(prefix='cobol-java-source-') as temporary:
                temporary_source = Path(temporary)/path.name
                temporary_source.write_text(adapted)
                args.append(str(temporary_source))
                proc = subprocess.run(args, cwd=target, capture_output=True, text=True)
                row['diagnostics'] = proc.stderr.replace(str(temporary_source), relative.as_posix()).strip()
                if proc.returncode == 0:
                    for generated in target.glob('*.java'):
                        code = generated.read_text()
                        code = code.replace(str(temporary_source), relative.as_posix())
                        code = code.replace('CobolUtil.cob_init(args, false);',
                                            'CobolUtil.cob_init(args, false);\n    CobolResolve.cobolInitCall();')
                        code = code.replace('CobolTerminal.display (', 'org.biggraph.cobol.CobolCompatibility.display (')
                        code = re.sub(r'(\w+)\.setField\s*\(', r'org.biggraph.cobol.CobolCompatibility.setDecimalField (\1, ', code)
                        code = re.sub(r'(f_\w+)\.add\s*\(', r'org.biggraph.cobol.CobolCompatibility.add (\1, ', code)
                        code = re.sub(r'(\bb_\w+(?:\.getSubDataStorage\([^;\n]*?\))?)\.getNumdisp\s*\((\d+)\)',
                                      r'org.biggraph.cobol.CobolCompatibility.numdisp(\1, \2)', code)
                        code = re.sub(r'(h_\w+)\.write\s*\(', r'org.biggraph.cobol.CobolCompatibility.write (\1, ', code)
                        code = re.sub(r'(h_\w+)\.close\s*\(', r'org.biggraph.cobol.CobolCompatibility.close (\1, ', code)
                        code = re.sub(r'(h_\w+)\.open\s*\(', r'org.biggraph.cobol.CobolCompatibility.open (\1, ', code)
                        if relative.as_posix()=='OpenCobol/ECBAP/cbl/PAYROL01.cbl':
                            code = code.replace('this.initialized = true;', 'org.biggraph.cobol.CobolCompatibility.registerStdout(h_PAYCHECK);\n      this.initialized = true;')
                            row['adaptations'].append('Implement ASSIGN TO DISPLAY with standard output record writes')
                        if relative.as_posix()=='OpenCobol/ECBAP/cbl/TABLE1.cbl':
                            converted=[]
                            for line in code.splitlines():
                                if 'CobolCompatibility.display (' in line:
                                    match=re.search(r'CobolIntrinsic.func(?:OrdMax|Mean|Range|Sum)\s*\(',line)
                                    if match:
                                        begin=match.start(); depth=1; end=match.end()
                                        while depth and end<len(line):
                                            depth+=(line[end]=='(')-(line[end]==')'); end+=1
                                        line=line[:begin]+'org.biggraph.cobol.CobolCompatibility.displayIntegralFunction('+line[begin:end]+')'+line[end:]
                                converted.append(line)
                            code='\n'.join(converted)+'\n'
                            row['adaptations'].append('Match direct integral array-function DISPLAY presentation to GnuCOBOL')
                        for method in ('sortTableInit', 'sortTableInitKey', 'sortTable'):
                            code = code.replace('CobolFileSort.'+method+' (',
                                                'org.biggraph.cobol.CobolCompatibility.'+method+' (')
                        code, count = re.subn(r'cob_sub_int\s*\((\w+),\s*', r'\1.subInt (', code)
                        if count:
                            row['adaptations'].append('Map compiler cob_sub_int emission to runtime AbstractCobolField.subInt')
                        generated.write_text('\n'.join(line.expandtabs(4).rstrip() for line in code.splitlines()) + '\n')
            if proc.returncode == 0:
                row['status'] = 'generated'
                row['classes'] = [package+'.'+p.stem for p in sorted(target.glob('*.java'))]
            else:
                ports={
                    'pgm_templates/CICS/basecics.cbl':'org.biggraph.cobol.cics.BaseCics',
                    'pgm_templates/CICS/calctest.cbl':'org.biggraph.cobol.cics.CalcTest',
                    'pgm_templates/CICS/slt_base_ibm_cics.cob':'org.biggraph.cobol.cics.GreetingTransaction',
                }
                if row['source'] in ports:
                    row['status']='ported_template'
                    row['classes']=[ports[row['source']]]
                    row['integration']='Requires the actual z/OS Connect OpenAPI contract, server and CICS map bindings'
                else: row['status'] = 'blocked_source'
                for p in target.glob('*.java'): p.unlink()
        results.append(row)
    REPORT.write_text(json.dumps(results, indent=2)+'\n')
    with (ROOT/'java/migration.csv').open('w', newline='') as f:
        w=csv.writer(f, lineterminator='\n')
        w.writerow(['cobol_source','java_classes','status'])
        for row in results: w.writerow([row['source'],';'.join(row['classes']),row['status']])
    from collections import Counter
    print(dict(Counter(r['status'] for r in results)))
    for row in results:
        if row['status']=='blocked_source':
            print(row['source'], row['diagnostics'][:900])

if __name__ == '__main__': main()
