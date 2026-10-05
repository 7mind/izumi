#!/usr/bin/env python3
from pathlib import Path
import argparse, hashlib, json, os, re, shutil, signal, subprocess
from xml.etree import ElementTree
ROOT = Path(__file__).resolve().parents[2]
TIMEOUT_SECONDS = 180
GRACE_SECONDS = 10
EXPECTED_CONTROL_COUNT = 2
SOURCE = r'''package fixture
import sbt.testing.{Event, EventHandler, Fingerprint, Framework, Logger, OptionalThrowable, Runner, Selector, Status, SubclassFingerprint, Task, TaskDef, TestSelector}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths, StandardOpenOption}
abstract class ThrowSpec
final class ThrowSuite extends ThrowSpec
final class ThrowFramework extends Framework {
  private val fingerprint = new SubclassFingerprint {
    override def isModule(): Boolean = false
    override def superclassName(): String = classOf[ThrowSpec].getName
    override def requireNoArgConstructor(): Boolean = true
  }
  override def name(): String = "generic-task-throw-control"
  override def fingerprints(): Array[Fingerprint] = Array(fingerprint)
  override def runner(arguments: Array[String], remoteArguments: Array[String], loader: ClassLoader): Runner = new Runner {
    override def args(): Array[String] = arguments.clone()
    override def remoteArgs(): Array[String] = remoteArguments.clone()
    override def done(): String = ""
    override def tasks(definitions: Array[TaskDef]): Array[Task] = definitions.map { definition => new Task {
      override def taskDef(): TaskDef = definition
      override def tags(): Array[String] = Array.empty
      override def execute(handler: EventHandler, loggers: Array[Logger]): Array[Task] = {
        handler.handle(new Event {
          override def fullyQualifiedName(): String = definition.fullyQualifiedName()
          override def fingerprint(): Fingerprint = definition.fingerprint()
          override def selector(): Selector = new TestSelector("buffered success")
          override def status(): Status = Status.Success
          override def throwable(): OptionalThrowable = new OptionalThrowable
          override def duration(): Long = 0L
        })
        val receipt = definition.fullyQualifiedName() + "\tSuccess\t" + ProcessHandle.current().pid()
        val _ = Files.write(Paths.get(sys.props("fixture.audit-root")).resolve("buffered.success"), receipt.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
        if (sys.props("fixture.mode") == "throw") throw new LinkageError("GENERIC_TASK_THROW_AFTER_BUFFERED_SUCCESS")
        Array.empty
      }
    }}
  }
}
'''
BUILD = '''scalaVersion := "3.9.0"
libraryDependencies += "org.scala-sbt" % "test-interface" % "1.0" % Test
scalacOptions ++= Seq("-release:17", "-Ybackend-parallelism", "1")
Test / fork := true
Test / testFrameworks := Seq(new TestFramework("fixture.ThrowFramework"))
Test / javaOptions ++= Seq("-Dfixture.mode=" + sys.props("fixture.mode"), "-Dfixture.audit-root=" + sys.props("fixture.audit-root"))
Test / testListeners += new TestsListener {
  override def doInit(): Unit = println("GENERIC_TASK_THROW_PARENT pid=" + ProcessHandle.current().pid())
  override def startGroup(name: String): Unit = println("GENERIC_TASK_THROW_START " + name)
  override def testEvent(event: TestEvent): Unit = println("GENERIC_TASK_THROW_EVENTS result=" + event.result + " count=" + event.detail.size)
  override def endGroup(name: String, cause: Throwable): Unit = println("GENERIC_TASK_THROW_END_THROWABLE " + name)
  override def endGroup(name: String, result: TestResult): Unit = println("GENERIC_TASK_THROW_END " + name + " result=" + result)
  override def doComplete(result: TestResult): Unit = println("GENERIC_TASK_THROW_COMPLETE result=" + result)
}
'''
def sha(path): return hashlib.sha256(Path(path).read_bytes()).hexdigest()
def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--evidence-dir',type=Path,required=True)
    args=parser.parse_args()
    evidence=args.evidence_dir.resolve(); evidence.mkdir(parents=True,exist_ok=False)
    shutil.copy2(__file__,evidence/'driver.py')
    outcomes=[]
    for sdk in ['2.0.9']:
        for mode in ['normal','throw']:
            lane=evidence/('sbt'+sdk+'-'+mode); build=lane/'build'
            (build/'project').mkdir(parents=True); (build/'src/test/scala').mkdir(parents=True)
            audit=lane/'audit'; audit.mkdir()
            (build/'build.sbt').write_text(BUILD)
            (build/'project/build.properties').write_text('sbt.version='+sdk+'\n')
            (build/'src/test/scala/ThrowFramework.scala').write_text(SOURCE)
            commands=['show Test / dependencyClasspath','show Test / fullClasspath','testOnly *ThrowSuite']
            if sdk=='2.0.9': commands.insert(0,'set Global / localCacheDirectory := file("'+str(lane/'local-cache')+'")')
            shell='task_sdk="$1"; shift; exec sbt --server --sbt-version "$task_sdk" -java-home "$JDK21" -batch -J-Xmx6G "$@"'
            argv=['direnv','exec',str(ROOT),'sh','-c',shell,'sdk-task-error-generic',sdk,'-Dfixture.mode='+mode,'-Dfixture.audit-root='+str(audit),*commands]
            inputs=[dict(path=str(p),sha256=sha(p)) for p in sorted(build.rglob('*')) if p.is_file()]
            (lane/'commands.json').write_text(json.dumps(dict(cwd=str(build),argv=argv,inputs=inputs),indent=2)+'\n')
            print('TASK_ERROR_GENERIC_LANE '+sdk+' '+mode,flush=True)
            with (lane/'run.log').open('x') as log:
                process=subprocess.Popen(argv,cwd=build,stdout=log,stderr=subprocess.STDOUT,start_new_session=True)
                try: actual=process.wait(timeout=TIMEOUT_SECONDS)
                except subprocess.TimeoutExpired:
                    os.killpg(process.pid,signal.SIGTERM)
                    try: process.wait(timeout=GRACE_SECONDS)
                    except subprocess.TimeoutExpired: os.killpg(process.pid,signal.SIGKILL); process.wait()
                    actual=124
                log.write('\nEXIT '+str(actual)+'\n')
            raw=(lane/'run.log').read_text()
            report_rows=[]
            for p in (build/'target').glob('**/test-reports/*.xml'):
                xml=ElementTree.parse(p)
                report_rows.append(dict(path=str(p),summary=xml.getroot().attrib,cases=[n.attrib for n in xml.findall('.//testcase')],errors=[n.attrib for n in xml.findall('.//error')]))
            sent=(audit/'buffered.success').read_text().split('\t') if (audit/'buffered.success').is_file() else []
            expected=0 if mode=='normal' else 1
            valid=actual==expected and len(sent)==3 and sent[:2]==['fixture.ThrowSuite','Success'] and raw.count('GENERIC_TASK_THROW_PARENT pid=')==1
            parents=re.findall(r'^GENERIC_TASK_THROW_PARENT pid=(\d+)$',raw,re.M)
            valid=valid and len(parents)==1 and len(sent)==3 and sent[2].isdigit() and sent[2]!=parents[0]
            if mode=='normal':
                status='Passed'
                valid=valid and raw.count('GENERIC_TASK_THROW_COMPLETE result='+status)==1 and len(report_rows)==1
                if report_rows:
                    row=report_rows[0]
                    valid=valid and row['summary']['tests']=='1' and row['summary']['errors']=='0' and row['summary']['failures']=='0' and len(row['cases'])==1 and row['cases'][0]['classname']=='fixture.ThrowSuite'
            else:
                valid=valid and not report_rows and 'GENERIC_TASK_THROW_START' not in raw and 'GENERIC_TASK_THROW_END' not in raw and 'GENERIC_TASK_THROW_EVENTS' not in raw and 'GENERIC_TASK_THROW_COMPLETE' not in raw and 'GENERIC_TASK_THROW_AFTER_BUFFERED_SUCCESS' in raw
            for row in inputs: assert sha(row['path'])==row['sha256']
            assert sha(__file__)==sha(evidence/'driver.py')
            record=dict(sbt=sdk,mode=mode,actualExit=actual,expectedExit=expected,valid=valid,sent=sent,reports=report_rows,listenerMarkers=[line for line in raw.splitlines() if line.startswith('GENERIC_TASK_THROW_')])
            (lane/'completion.json').write_text(json.dumps(record,indent=2)+'\n'); outcomes.append(record)
            print(json.dumps({k:v for k,v in record.items() if k not in ['reports','listenerMarkers']}),flush=True)
            if not valid: break
        if not outcomes[-1]['valid']: break
    valid=len(outcomes)==EXPECTED_CONTROL_COUNT and all(row['valid'] for row in outcomes)
    terminal=dict(exit=0 if valid else 1,outcomes=outcomes,scope='Expected-defect control. Public generic test-interface and TestsListener only; no distage dependencies/plugin/private SDK changes. Driver0 reproduces missing SBT2 completion on task Throwable, not product acceptance.')
    (evidence/'completion.json').write_text(json.dumps(terminal,indent=2)+'\n')
    print(json.dumps(dict(exit=terminal['exit'],completionSha256=sha(evidence/'completion.json'))),flush=True)
    raise SystemExit(terminal['exit'])
if __name__=='__main__': main()
