#!/usr/bin/env python3
import hashlib,json,shutil,subprocess
from audit import audit
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor
import argparse
parser=argparse.ArgumentParser()
parser.add_argument('--repo-root',type=Path,required=True)
parser.add_argument('--artifact-version',required=True)
parser.add_argument('--production-host-version',required=True)
parser.add_argument('--platform',nargs='+',choices=['jvm','js','native'],required=True)
parser.add_argument('--scala-version',nargs='+',choices=['3.9.0','2.13.18'],required=True)
parser.add_argument('--evidence-dir',type=Path,required=True)
args=parser.parse_args()
ROOT=args.repo_root.resolve();OUT=args.evidence_dir.resolve();TEMPLATE=Path(__file__).resolve().parent;OUT.mkdir()
inputs=[dict(path=str(path),sha256=hashlib.sha256(path.read_bytes()).hexdigest()) for path in TEMPLATE.rglob('*') if path.is_file()]
commands=[]
cases=[(compiler,platform,fork) for compiler in args.scala_version for platform in args.platform if platform=='jvm' or compiler.startswith('2.') for fork in ([False,True] if platform=='jvm' else [False])]
if not cases:
 parser.error('No supported coverage combinations were selected; Scala 3 coverage requires JVM')
for compiler,platform,fork in cases:
  label=compiler+('-fork' if fork else '-in-process') if platform=='jvm' else compiler+'-'+platform;build=OUT/label;shutil.copytree(TEMPLATE,build)
  helper=ROOT/'project/ScoverageCompilerDependencies.scala';shutil.copyfile(helper,build/'project/ScoverageCompilerDependencies.scala');inputs.append(dict(path=str(helper),sha256=hashlib.sha256(helper.read_bytes()).hexdigest()))
  assertion=ROOT/'fundamentals/fundamentals-assertions/src/test'
  for variant in ['scala','scala-'+('3' if compiler.startswith('3.') else '2')]:
   source=assertion/variant/'izumi/fundamentals/assertions';destination=build/'witness/src/main'/variant/'izumi/fundamentals/assertions';destination.mkdir(parents=True)
   for name in ['AssertionFixtures.scala','AssertionInlineFixture.scala']:
    path=source/name
    if path.is_file():shutil.copyfile(path,destination/name);inputs.append(dict(path=str(path),sha256=hashlib.sha256(path.read_bytes()).hexdigest()))
  if platform!='jvm':
   for path in build.glob('*/src/test/scala/coveragefixture/*.scala'):path.write_text(path.read_text().replace('fork=" + ProcessHandle.current().pid()', 'platform='+platform+'"'))
  requests=['coverageOff','testFull','coverage','testFull','coverageReport','coverageAggregate','captureCoverage','coverageOff','clean','witness/publishLocal','right/publishLocal']
  argv=['direnv','exec',str(ROOT),'sh','-c','exec sbt --server --sbt-version 2.0.9 -java-home "$JDK21" -batch -J-Xmx6G "$@"','coverage','-Dcoverage.platform='+platform,'-Dcoverage.artifact-version='+args.artifact_version,'-Dcoverage.host-version='+args.production_host_version,'-Dcoverage.scala='+compiler,'-Dcoverage.fork='+str(fork).lower(),'-Dcoverage.version=0.0.0-'+OUT.name+'-'+label+'-SNAPSHOT',*requests]
  commands.append(dict(label=label,scala=compiler,platform=platform,fork=fork,cwd=str(build),argv=argv))
  inputs += [dict(path=str(path),sha256=hashlib.sha256(path.read_bytes()).hexdigest()) for path in build.rglob('*') if path.is_file()]
(OUT/'commands.json').write_text(json.dumps(dict(inputs=inputs,commands=commands),indent=2)+'\n')
def run(command):
 with (OUT/(command['label']+'.log')).open('x') as log:r=subprocess.run(command['argv'],cwd=command['cwd'],stdout=log,stderr=subprocess.STDOUT,timeout=1800)
 record=dict(label=command['label'],actualExit=r.returncode);print(json.dumps(record),flush=True);return record
with ThreadPoolExecutor(max_workers=2) as pool:records=list(pool.map(run,commands))
changed=[row['path'] for row in inputs if hashlib.sha256(Path(row['path']).read_bytes()).hexdigest()!=row['sha256']]
(OUT/'completion.json').write_text(json.dumps(dict(lanes=records,inputsChanged=changed),indent=2)+'\n')
assert not changed and all(row['actualExit']==0 for row in records),records
report=audit(commands,OUT)
(OUT/'audit.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps(dict(lanes=len(commands),reports=report['reports'],normalPublications=report['normalPublications'])),flush=True)
