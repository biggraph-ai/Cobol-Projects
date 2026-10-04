#!/usr/bin/env python3
"""Run a converted program using its exact original repository path."""
import argparse,json,os,re,subprocess,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source',help='Original .cbl/.cob path; avoids ambiguous PROGRAM-ID variants')
    parser.add_argument('--cwd',type=Path,required=True,help='Working directory containing mapped input/output files')
    parser.add_argument('--dd',action='append',default=[],metavar='NAME=PATH',help='Mainframe DD dataset binding; repeat as needed')
    parser.add_argument('args',nargs='*',help='Arguments to the converted program')
    options=parser.parse_args()
    rows=json.loads((ROOT/'java/conversion.json').read_text())
    selected=next((r for r in rows if r['source']==options.source),None)
    if selected is None:parser.error('Unknown source path')
    if selected['status']!='generated':parser.error('This source is '+selected['status']+'; see java/conversion.json')
    env=os.environ.copy()
    env['COB_PACKAGE_PATH']=os.pathsep.join(r['package'] for r in rows if Path(r['source']).parent==Path(selected['source']).parent and r['status']=='generated')
    for binding in options.dd:
        name,sep,value=binding.partition('=')
        if not sep or not re.fullmatch(r'[A-Za-z0-9_-]+',name):parser.error('--dd must be NAME=PATH with an alphanumeric DD name')
        env['DD_'+name]=str(Path(value).resolve())
    classes=Path(env.get('JAVA_BUILD_DIR','/workspace/cobol-java-build'))/'classes'
    runtime=Path(env.get('COBOL4J_HOME','/workspace/toolchains/cobol4j'))/'lib/*'
    if not classes.is_dir():parser.error('Run bash java/build.sh first')
    process=subprocess.run(['java','-cp',str(classes)+os.pathsep+str(runtime),selected['classes'][0],*options.args],cwd=options.cwd,env=env)
    return process.returncode
if __name__=='__main__':raise SystemExit(main())
