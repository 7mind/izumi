from pathlib import Path
from xml.etree import ElementTree
import argparse
import hashlib
import importlib.util
import json
import os
import shutil
import signal
import subprocess

ROOT = Path(__file__).resolve().parents[2]
TIMEOUT_SECONDS = 600
spec = importlib.util.spec_from_file_location('command_groups', Path(__file__).with_name('verify-command-groups.py'))
groups = importlib.util.module_from_spec(spec)
spec.loader.exec_module(groups)
SOURCE = groups.SOURCE.replace('        Array.empty\n', '''        if (mode == "task-error" && definition.fullyQualifiedName() == "fixture.SuiteA") {
          val written = Files.write(audit.resolve("task.throw"),pid.getBytes(StandardCharsets.UTF_8),StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)
          require(Files.isRegularFile(written),"Task throw marker missing")
          throw new LinkageError("FOREIGN_TASK_THROW_AFTER_BUFFERED_SUCCESSES")
        }
        Array.empty
''')
BUILD = groups.BUILD.split('Test / testSelected / testResultLogger :=')[0].replace(
    'def group(name: String, tests: Seq[TestDefinition]) = new Tests.Group(name, tests, Tests.SubProcess(options))',
    'def group(name: String, tests: Seq[TestDefinition]) = new Tests.Group(name, tests, if ((Test / fork).value) Tests.SubProcess(options) else Tests.InProcess)'
).replace('case "serial" | "halt" | "exit" | "exit-one" =>', 'case "normal" | "task-error" | "recovery" | "serial" | "halt" | "exit" | "exit-one" =>').replace(
    'case "single" | "recovery" | "empty" =>', 'case "single" | "empty" =>'
) + r'''
Test / testSelected / testResultLogger := {
  val inherited = (Test / testSelected / testResultLogger).value
  new TestResultLogger {
    override def run(log: sbt.util.Logger, output: Tests.Output, taskName: String): Unit = {
      val audit = file(sys.props("fixture.audit-root"))
      IO.write(audit / "host.parent",ProcessHandle.current().pid().toString)
      val rows = output.events.toVector.sortBy(_._1).map { case (name,r) => Vector(name,r.result.toString,r.passedCount,r.failureCount,r.errorCount).mkString("\t") }
      IO.write(audit / "host.output",rows.mkString("\n"))
      inherited.run(log,output,taskName)
    }
  }
}
val prepareTaskFailure = inputKey[Unit]("Prepare a foreign task failure control")
prepareTaskFailure := {
  val parsed = sbt.complete.DefaultParsers.spaceDelimited("mode").parsed
  require(parsed.size == 1,"Missing task-failure mode")
  val audit = file(sys.props("fixture.audit-root"))
  IO.delete(audit); IO.createDirectory(audit)
  IO.delete((Test / target).value / "test-reports")
  IO.write(audit / "mode",parsed.head)
}
val observeTaskFailure = taskKey[Unit]("Capture a rejected command in the same SBT session")
observeTaskFailure := Def.uncached {
  val result = (Test / testOnly).toTask(" fixture.SuiteA fixture.SuiteB").result.value.toEither
  require(result.isLeft,"FOREIGN_TASK_FAILURE_FALSE_SUCCESS")
  IO.write(file(sys.props("fixture.audit-root")) / "host.rejected",result.left.toOption.get.toString)
}
val captureTaskFailure = inputKey[Unit]("Freeze physical bodies and host reports")
captureTaskFailure := {
  val parsed = sbt.complete.DefaultParsers.spaceDelimited("case").parsed
  require(parsed.size == 1,"Missing capture case")
  val destination = file(sys.props("fixture.captures")) / parsed.head
  require(!destination.exists(),"Capture must be new")
  IO.copyDirectory(file(sys.props("fixture.audit-root")),destination / "audit")
  IO.copyDirectory((Test / target).value / "test-reports",destination / "test-reports")
  val roots = (Test / target).value / "distage-fork-receipts"
  require(!roots.exists() || (roots * "*").get().isEmpty,"Command root survived cleanup")
}
'''

def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--artifact-version', required=True)
    parser.add_argument('--scala-version', nargs='+', choices=['3.9.0','2.13.18'], required=True)
    parser.add_argument('--expected-task-report', choices=['absent','error'], required=True)
    parser.add_argument('--evidence-dir', type=Path, required=True)
    args = parser.parse_args()
    out = args.evidence_dir.resolve(); out.mkdir()
    shutil.copy2(__file__,out/'driver.py')
    shutil.copy2(Path(__file__).with_name('verify-command-groups.py'),out/'verify-command-groups.py')
    outcomes = []
    for scala in args.scala_version:
        lane = out/('scala'+scala); build = lane/'build'; (build/'project').mkdir(parents=True)
        source = build/'src/test/scala/GroupFramework.scala'; source.parent.mkdir(parents=True)
        source.write_text(SOURCE); (build/'build.sbt').write_text(BUILD)
        (build/'project/build.properties').write_text('sbt.version=2.0.9\n')
        (build/'project/plugins.sbt').write_text('addSbtPlugin("io.7mind.izumi" % "sbt-distage-testkit" % "'+args.artifact_version+'")\n')
        inputs = [dict(path=str(p),sha256=sha(p)) for p in sorted(build.rglob('*')) if p.is_file()]
        commands = ['set Global / localCacheDirectory := file("'+str(lane/'local-cache')+'")']; cases = []
        for fork in [False,True]:
            commands += ['set Test / fork := '+str(fork).lower()]
            for mode in ['normal','task-error','recovery']:
                name = ('fork-' if fork else 'inprocess-')+mode; cases.append(dict(name=name,fork=fork,mode=mode))
                request = 'observeTaskFailure' if mode=='task-error' else 'testOnly fixture.SuiteA fixture.SuiteB'
                commands += ['prepareTaskFailure '+mode,request,'captureTaskFailure '+name]
        argv = ['direnv','exec',str(ROOT),'sh','-c','exec sbt --server --sbt-version 2.0.9 -java-home "$JDK21" -batch -J-Xmx6G "$@"','foreign-task-failure','-Dfixture.scala-version='+scala,'-Dfixture.artifact-version='+args.artifact_version,'-Dfixture.audit-root='+str(build/'audit'),'-Dfixture.captures='+str(lane/'cases'),*commands]
        (lane/'commands.json').write_text(json.dumps(dict(cwd=str(build),argv=argv,inputs=inputs,cases=cases),indent=2)+'\n')
        print('FOREIGN_TASK_FAILURE_BATCH_START '+scala,flush=True)
        with (lane/'run.log').open('x') as log:
            process = subprocess.Popen(argv,cwd=build,stdout=log,stderr=subprocess.STDOUT,start_new_session=True)
            try: code = process.wait(timeout=TIMEOUT_SECONDS)
            except subprocess.TimeoutExpired:
                os.killpg(process.pid,signal.SIGTERM)
                try: process.wait(timeout=10)
                except subprocess.TimeoutExpired: os.killpg(process.pid,signal.SIGKILL); process.wait()
                code = 124
            log.write('\nEXIT '+str(code)+'\n')
        failures = []; checks = []; parents = set(); children = set()
        if code: failures.append('SBT failed: inspect run.log')
        else:
            for row in cases:
                try:
                    audit = lane/'cases'/row['name']/'audit'
                    bodies = [p.read_text().split('\t') for p in sorted(audit.glob('*.body'))]
                    body_counts = {s:sum(r[0]==s for r in bodies) for s in ['fixture.SuiteA','fixture.SuiteB']}
                    failed = row['mode']=='task-error'
                    absent = failed and args.expected_task_report=='absent'
                    assert body_counts['fixture.SuiteA']==3
                    assert body_counts['fixture.SuiteB'] in ([0,3] if absent else [3])
                    assert (audit/'host.rejected').is_file()==failed
                    assert (audit/'task.throw').is_file()==failed
                    reports = [ElementTree.parse(p) for p in (lane/'cases'/row['name']/'test-reports').glob('*.xml')]
                    xml = [n for report in reports for n in report.findall('.//testcase')]
                    successes = [n for n in xml if n.find('error') is None and n.find('failure') is None and n.find('skipped') is None]
                    errors = [n for n in xml if n.find('error') is not None]
                    if absent:
                        assert all(n.attrib['classname']=='fixture.SuiteB' for n in xml)
                        assert len(xml) in [0,3] and not errors
                    else:
                        assert sorted((n.attrib['classname'],n.attrib['name']) for n in successes)==[(s,'body-'+str(i)) for s in body_counts for i in range(1,4)]
                        assert len(errors)==int(failed)
                        if failed:
                            assert errors[0].attrib['classname']=='fixture.SuiteA'
                            assert 'FOREIGN_TASK_THROW_AFTER_BUFFERED_SUCCESSES' in ElementTree.tostring(errors[0],encoding='unicode')
                        output = (audit/'host.output').read_text().splitlines()
                        assert output==['fixture.SuiteA\t'+('Error' if failed else 'Passed')+'\t3\t0\t'+str(int(failed)),'fixture.SuiteB\tPassed\t3\t0\t0']
                    if (audit/'host.parent').is_file(): parents.add((audit/'host.parent').read_text())
                    pids = {r[2] for r in bodies}
                    if row['fork']:
                        assert not pids & children; children |= pids
                        for pid in pids:
                            try: os.kill(int(pid),0); raise AssertionError('Worker survived command')
                            except ProcessLookupError: pass
                    else:
                        assert len(pids)==1
                    assert not list(audit.glob('*.terminal'))
                    checks.append(dict(case=row['name'],bodies=len(bodies),xmlCases=len(xml),errors=len(errors),pids=sorted(pids)))
                except (AssertionError,OSError,ValueError) as cause:
                    failures.append(row['name']+': '+str(cause))
            if len(parents)!=1: failures.append('Host session changed')
        for row in inputs: assert sha(row['path'])==row['sha256']
        result = dict(scala=scala,actualExit=code,expectedTaskReport=args.expected_task_report,failures=failures,checks=checks)
        (lane/'completion.json').write_text(json.dumps(result,indent=2)+'\n'); outcomes.append(result); print(json.dumps(result),flush=True)
    result = dict(exit=0 if all(not r['actualExit'] and not r['failures'] for r in outcomes) else 1,outcomes=outcomes)
    (out/'completion.json').write_text(json.dumps(result,indent=2)+'\n'); raise SystemExit(result['exit'])

if __name__=='__main__': main()
