#!/usr/bin/env python3
from pathlib import Path
from xml.etree import ElementTree
import argparse
import hashlib
import json
import os
import shutil
import signal
import subprocess
import uuid

ROOT = Path(__file__).resolve().parents[2]
TIMEOUT_SECONDS = 600
PREFIX = 'izumi.fixtures.host.'

FRAMEWORK = r'''package izumi.fixtures.host

import izumi.distage.testkit.protocol.ForkReceiptArguments
import sbt.testing.{EventHandler, Fingerprint, Framework, Logger, Runner, Task, TaskDef}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths, StandardOpenOption}

final class ThrowingTaskFramework extends Framework {
  private val delegate = new izumi.distage.testkit.runner.bootstrap.Framework
  override def name(): String = delegate.name()
  override def fingerprints(): Array[Fingerprint] = delegate.fingerprints()
  override def runner(args: Array[String], remote: Array[String], loader: ClassLoader): Runner = {
    val invocation = ForkReceiptArguments.parse(args.toVector, remote.toVector)
    val original = delegate.runner(args, remote, loader)
    val audit = Paths.get(sys.props("izumi.fixture.audit-root"))
    new Runner {
      override def args(): Array[String] = original.args()
      override def remoteArgs(): Array[String] = original.remoteArgs()
      override def tasks(definitions: Array[TaskDef]): Array[Task] = {
        val names = definitions.map(_.fullyQualifiedName()).sorted
        val data = (Vector(ProcessHandle.current().pid().toString) ++ names.toVector).mkString("\n")
        val _ = Files.write(audit.resolve("target.selected"), data.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
        original.tasks(definitions.sortBy(_.fullyQualifiedName())).map { task => new Task {
          override def taskDef(): TaskDef = task.taskDef()
          override def tags(): Array[String] = task.tags()
          override def execute(handler: EventHandler, loggers: Array[Logger]): Array[Task] = {
            val children = task.execute(handler,loggers)
            if (Files.isRegularFile(audit.resolve("throw.flag")) && task.taskDef().fullyQualifiedName() == "izumi.fixtures.host.SuiteC") {
              val data = ProcessHandle.current().pid().toString + "\tSDK_TASK_THROW_AFTER_COMPLETE_BODY_GROUP"
              val _ = Files.write(audit.resolve("task.throw"),data.getBytes(StandardCharsets.UTF_8),StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)
              throw new LinkageError("SDK_TASK_THROW_AFTER_COMPLETE_BODY_GROUP")
            }
            children
          }
        }}
      }
      override def done(): String = {
        val result = original.done()
        invocation.hostDirectory.foreach { directory =>
          val entries = Files.list(directory)
          try entries.forEach { path =>
            if (path.getFileName.toString.endsWith(".terminal")) {
              val captured = audit.resolve(path.getFileName)
              if (Files.exists(captured)) require(Files.mismatch(path,captured) == -1L,"Repeated terminal capture differs")
              else { val _ = Files.copy(path,captured); () }
            }
          } finally entries.close()
        }
        result
      }
    }
  }
}
'''

SETTINGS = r'''
lazy val taskFailureConsumer = project.in(file(".")).enablePlugins(izumi.distage.sbt.DistageTestkitPlugin)
val proxyFramework = new TestFramework("izumi.fixtures.host.ThrowingTaskFramework")
Test / testFrameworks := Seq(proxyFramework)
def proxyExecution(value: Tests.Execution): Tests.Execution = value.copy(options = value.options.map {
  case Tests.Argument(Some(framework), arguments) if framework == new TestFramework("izumi.distage.testkit.runner.bootstrap.Framework") => Tests.Argument(proxyFramework, arguments*)
  case other => other
})
Test / testSelected / testExecution := Def.uncached { proxyExecution((Test / testSelected / testExecution).value) }
Test / testQuick / testExecution := Def.uncached { proxyExecution((Test / testQuick / testExecution).value) }
Test / test / testExecution := Def.uncached { proxyExecution((Test / test / testExecution).value) }
Test / testResultLogger := {
  val audit = file(sys.props("izumi.fixture.audit-root"))
  new TestResultLogger {
    override def run(log: sbt.util.Logger, output: Tests.Output, taskName: String): Unit = {
      IO.write(audit / "host.parent",ProcessHandle.current().pid().toString)
      val rows = output.events.toVector.sortBy(_._1).map { case (name,result) => Vector(name,result.passedCount,result.failureCount,result.errorCount).mkString("\t") }
      IO.write(audit / "host.output",rows.mkString("\n"))
      TestResultLogger.Default.run(log,output,taskName)
    }
  }
}
val prepareTaskFailure = inputKey[Unit]("Prepare one owned task-failure control")
prepareTaskFailure := {
  val parsed = spaceDelimited("mode").parsed
  require(parsed.size == 1 && Set("normal","task-error","recovery").contains(parsed.head),"Missing task-failure mode")
  val audit = file(sys.props("izumi.fixture.audit-root"))
  IO.delete(audit); IO.createDirectory(audit)
  IO.delete((Test / target).value / "test-reports")
  if (parsed.head == "task-error") IO.write(audit / "throw.flag","enabled")
}
val observeTaskFailure = taskKey[Unit]("Capture a rejected command without leaving the SBT session")
observeTaskFailure := Def.uncached {
  val result = (Test / testOnly).toTask(" *SuiteC *SuiteD *SuiteE").result.value
  require(result.toEither.isLeft,"TASK_FAILURE_FALSE_SUCCESS")
  IO.write(file(sys.props("izumi.fixture.audit-root")) / "host.rejected",result.toEither.left.toOption.get.toString)
}
val captureTaskFailure = inputKey[Unit]("Freeze physical bodies and original host reports")
captureTaskFailure := {
  val parsed = spaceDelimited("case").parsed
  require(parsed.size == 1,"Missing task-failure case")
  val destination = file(sys.props("izumi.fixture.captures")) / parsed.head
  require(!destination.exists(),"Task-failure capture must be new")
  IO.copyDirectory(file(sys.props("izumi.fixture.audit-root")),destination / "audit")
  IO.copyDirectory((Test / target).value / "test-reports",destination / "test-reports")
  val roots = (Test / target).value / "distage-fork-receipts"
  require(!roots.exists() || (roots * "*").get().isEmpty,"Task-failure command root survived")
}
'''

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--artifact-version',required=True)
    parser.add_argument('--scala-version',nargs='+',choices=['3.9.0','2.13.18'],required=True)
    parser.add_argument('--expected-task-report',choices=['absent','error'],required=True)
    parser.add_argument('--evidence-dir',type=Path,required=True)
    args = parser.parse_args(); out = args.evidence_dir.resolve(); out.mkdir()
    shutil.copy2(__file__,out/'driver.py')
    fixture = ROOT/'test-fixtures/host-sharing-consumer'; outcomes = []
    for scala in args.scala_version:
        lane = out/('scala'+scala); build = lane/'build'; (build/'project').mkdir(parents=True)
        for source in sorted((fixture/'src').rglob('*.scala')):
            target = build/source.relative_to(fixture); target.parent.mkdir(parents=True,exist_ok=True); shutil.copy2(source,target)
        (build/'src/test/scala/izumi/fixtures/host/ThrowingTaskFramework.scala').write_text(FRAMEWORK)
        settings = (fixture/'build.sbt').read_text()
        start = settings.index('Test / testFrameworks :=')
        end = settings.index('Test / javaOptions +=',start)
        (build/'build.sbt').write_text(settings[:start]+settings[end:]+SETTINGS)
        (build/'project/build.properties').write_text('sbt.version=2.0.9\n')
        (build/'project/plugins.sbt').write_text('addSbtPlugin("io.7mind.izumi" % "sbt-distage-testkit" % "'+args.artifact_version+'")\n')
        commands = ['set Global / localCacheDirectory := file("'+str(lane/'local-cache')+'")']; cases = []
        for fork in [False,True]:
            commands += ['set Test / fork := '+str(fork).lower()]
            for mode in ['normal','task-error','recovery']:
                name = ('fork-' if fork else 'inprocess-')+mode; cases.append(dict(name=name,fork=fork,mode=mode))
                request = 'observeTaskFailure' if mode=='task-error' else 'testOnly *SuiteC *SuiteD *SuiteE'
                commands += ['prepareTaskFailure '+mode,request,'captureTaskFailure '+name]
        inputs = [dict(path=str(p),sha256=hashlib.sha256(p.read_bytes()).hexdigest()) for p in sorted(build.rglob('*')) if p.is_file()]
        argv = ['direnv','exec',str(ROOT),'sh','-c','exec sbt --server --sbt-version 2.0.9 -java-home "$JDK21" -batch -J-Xmx6G "$@"','task-failure','-Dizumi.fixture.scala-version='+scala,'-Dizumi.fixture.version='+args.artifact_version,'-Dizumi.fixture.audit-root='+str(build/'target/body-audit'),'-Dizumi.fixture.captures='+str(lane/'cases'),*commands]
        (lane/'commands.json').write_text(json.dumps(dict(cwd=str(build),argv=argv,inputs=inputs,cases=cases),indent=2)+'\n')
        print('TASK_FAILURE_BATCH_START '+scala,flush=True)
        with (lane/'run.log').open('x') as log:
            process = subprocess.Popen(argv,cwd=build,stdout=log,stderr=subprocess.STDOUT,start_new_session=True)
            try: code = process.wait(timeout=TIMEOUT_SECONDS)
            except subprocess.TimeoutExpired:
                os.killpg(process.pid,signal.SIGTERM)
                try: process.wait(timeout=10)
                except subprocess.TimeoutExpired: os.killpg(process.pid,signal.SIGKILL); process.wait()
                code = 124
        failures = []; checks = []; resources = set(); parents = set(); children = set()
        if code: failures.append('SBT process failed: inspect run.log')
        else:
            for row in cases:
                case = lane/'cases'/row['name']; audit = case/'audit'; missing = row['mode']=='task-error'
                labels = ['SuiteC','SuiteD','SuiteE']
                bodies = [p.read_text().split('\t') for p in audit.glob('*.body')]
                assert sorted((r[0],r[1]) for r in bodies)==sorted((PREFIX+s,str(i)) for s in labels for i in range(1,4))
                acquired = [p.read_text() for p in audit.glob('*.acquire')]; released = [p.read_text() for p in audit.glob('*.release')]
                assert len(acquired)==1 and acquired==released and not resources.intersection(acquired)
                resources.update(acquired)
                selected = (audit/'target.selected').read_text().splitlines(); target_pid = selected[0]
                assert selected[1:]==[PREFIX+s for s in labels]
                reports = [ElementTree.parse(p) for p in (case/'test-reports').glob('*.xml')]
                passed = [(n.attrib['classname'],n.attrib['name']) for x in reports for n in x.findall('.//testcase') if n.find('error') is None]
                errors = [n for x in reports for n in x.findall('.//testcase') if n.find('error') is not None]
                assert all(not x.findall('.//failure') and not x.findall('.//skipped') for x in reports)
                expected_labels = sorted({name.removeprefix(PREFIX) for name,_ in passed}) if missing and args.expected_task_report=='absent' else labels
                if missing and args.expected_task_report=='absent': assert set(expected_labels)<= {'SuiteD','SuiteE'}
                assert sorted(passed)==sorted((PREFIX+s,'equal display name should '+leaf) for s in expected_labels for leaf in ['first','second','third'])
                if missing:
                    assert (audit/'host.rejected').is_file() and (audit/'task.throw').read_text()==target_pid+'\tSDK_TASK_THROW_AFTER_COMPLETE_BODY_GROUP'
                    if args.expected_task_report=='absent':
                        assert not errors and not (audit/'host.output').exists()
                        assert 'SDK_TASK_THROW_AFTER_COMPLETE_BODY_GROUP' in (audit/'host.rejected').read_text()
                    else:
                        assert len(errors)==1 and errors[0].attrib['classname']==PREFIX+'SuiteC'
                        assert 'SDK_TASK_THROW_AFTER_COMPLETE_BODY_GROUP' in ''.join(errors[0].itertext())
                else: assert not errors and not (audit/'host.rejected').exists() and not (audit/'task.throw').exists()
                terminals = []
                if args.expected_task_report=='error' or not missing:
                    for path in sorted(audit.glob('*.terminal')):
                        fields = path.read_text().split('\t')
                        assert len(fields)==12 and fields[0]=='1' and path.name=='target-'+str(uuid.UUID(fields[1]))+'.terminal'
                        assert fields[3]==target_pid
                        failed = missing and fields[2]==PREFIX+'SuiteC'
                        assert fields[4]==('false' if failed else 'true')
                        assert [int(value) for value in fields[5:]]==[3,0,1 if failed else 0,0,0,0,0]
                        terminals.append(fields[2])
                    assert sorted(terminals)==[PREFIX+s for s in labels]
                    output = [line.split('\t') for line in (audit/'host.output').read_text().splitlines()]
                    assert output==[[PREFIX+s,'3','0','1' if missing and s=='SuiteC' else '0'] for s in labels]
                if (audit/'host.parent').is_file():
                    parent = (audit/'host.parent').read_text(); parents.add(parent)
                    assert (target_pid!=parent)==row['fork']
                if row['fork']:
                    assert target_pid not in children; children.add(target_pid)
                    try: os.kill(int(target_pid),0); raise AssertionError('Target survived command')
                    except ProcessLookupError: pass
                checks.append(dict(case=row['name'],bodies=len(bodies),xmlCases=len(passed)+len(errors),errors=len(errors),targetPid=target_pid,terminalSuites=terminals))
            assert len(parents)==1
        for row in inputs: assert hashlib.sha256(Path(row['path']).read_bytes()).hexdigest()==row['sha256']
        result = dict(scala=scala,actualExit=code,expectedTaskReport=args.expected_task_report,failures=failures,checks=checks)
        (lane/'completion.json').write_text(json.dumps(result,indent=2)+'\n'); outcomes.append(result); print(json.dumps(result),flush=True)
        if code: break
    result = dict(exit=0 if len(outcomes)==len(args.scala_version) and all(not r['actualExit'] for r in outcomes) else 1,outcomes=outcomes)
    (out/'completion.json').write_text(json.dumps(result,indent=2)+'\n'); raise SystemExit(result['exit'])

if __name__=='__main__': main()
