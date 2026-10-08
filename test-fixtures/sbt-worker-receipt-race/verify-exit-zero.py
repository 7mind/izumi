#!/usr/bin/env python3
from pathlib import Path
import argparse
import json
import re
from xml.etree import ElementTree
import sys
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from fixture_harness import write_sbt_project, freeze_driver, run_process, sha
from fixture_framework import framework_source

ROOT=Path(__file__).resolve().parents[2]
EVENT_COUNT=15
LANE_TIMEOUT_SECONDS=300
SHUTDOWN_GRACE_SECONDS=10
SOURCE = framework_source(
    prefix=r'''package fixture
import sbt.testing.{Event, EventHandler, Fingerprint, Framework, Logger, OptionalThrowable, Runner, Selector, Status, SubclassFingerprint, Task, TaskDef, TestSelector}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths, StandardOpenOption}
abstract class SuccessSpec
final class SuccessSuite extends SuccessSpec
''',
    framework='SuccessFramework', fingerprint='fingerprint', superclass='classOf[SuccessSpec].getName', label='"exit-zero-control"',
    members='', arguments='arguments', remote='remoteArguments', definitions='definitions',
    execute=r'''        val directory = Paths.get(sys.props("fixture.audit-root"))
        (1 to 15).foreach { index =>
          handler.handle(new Event {
            override def fullyQualifiedName(): String = definition.fullyQualifiedName()
            override def fingerprint(): Fingerprint = definition.fingerprint()
            override def selector(): Selector = new TestSelector("case " + index)
            override def status(): Status = Status.Success
            override def throwable(): OptionalThrowable = new OptionalThrowable()
            override def duration(): Long = 0L
          })
          val data = definition.fullyQualifiedName() + "\t" + index + "\tSuccess\t" + ProcessHandle.current().pid()
          val _ = Files.write(directory.resolve(index + ".sent"), data.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
        }
        if (sys.props("fixture.mode") == "halt") {
          println("GENERIC_EXIT_ZERO_HALT pid=" + ProcessHandle.current().pid() + " sent=15 exit=0")
          Runtime.getRuntime.halt(0)
        }
''',
    done='"control complete"', tags='Array.empty[String]', empty_tasks='Array.empty[Task]',
)
BUILD='''scalaVersion := "3.9.0"
libraryDependencies += "org.scala-sbt" % "test-interface" % "1.0" % Test
scalacOptions ++= Seq("-release:17", "-Ybackend-parallelism", "1")
Test / fork := true
Test / testFrameworks := Seq(new TestFramework("fixture.SuccessFramework"))
Test / javaOptions ++= Seq("-Dfixture.mode=" + sys.props("fixture.mode"), "-Dfixture.audit-root=" + sys.props("fixture.audit-root"))
Test / testListeners += new TestsListener {
  override def doInit(): Unit = println("GENERIC_EXIT_ZERO_PARENT pid=" + ProcessHandle.current().pid())
  override def startGroup(name: String): Unit = println("GENERIC_EXIT_ZERO_START " + name)
  override def testEvent(event: TestEvent): Unit = println("GENERIC_EXIT_ZERO_EVENTS result=" + event.result + " count=" + event.detail.size)
  override def endGroup(name: String, cause: Throwable): Unit = println("GENERIC_EXIT_ZERO_END_THROWABLE " + name)
  override def endGroup(name: String, result: TestResult): Unit = println("GENERIC_EXIT_ZERO_END " + name + " result=" + result)
  override def doComplete(result: TestResult): Unit = println("GENERIC_EXIT_ZERO_COMPLETE result=" + result)
}
'''

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--evidence-dir',required=True,type=Path)
    arguments=parser.parse_args()
    evidence=arguments.evidence_dir.resolve()
    evidence.mkdir(parents=True,exist_ok=False)
    freeze_driver(__file__,evidence/'driver.py')
    outcomes=[]
    for mode in ['normal','halt']:
        lane=evidence/mode
        build=lane/'build'
        (build/'src/test/scala').mkdir(parents=True)
        audit=lane/'audit'
        audit.mkdir()
        (build/'src/test/scala/SuccessFramework.scala').write_text(SOURCE)
        write_sbt_project(build, BUILD, '2.0.9', None)
        commands=['set Global / localCacheDirectory := file("'+str(lane/'local-cache')+'")','show Test / dependencyClasspath','testFull','show Test / fullClasspath']
        shell='task_sdk="$1"; shift; exec sbt --server --sbt-version "$task_sdk" -java-home "$JDK21" -batch -J-Xmx6G "$@"'
        argv=['direnv','exec',str(ROOT),'sh','-c',shell,'exit-zero-minimal','2.0.9','-Dfixture.mode='+mode,'-Dfixture.audit-root='+str(audit),*commands]
        inputs=[dict(path=str(p),sha256=sha(p)) for p in sorted(build.rglob('*')) if p.is_file()]
        (lane/'commands.json').write_text(json.dumps(dict(cwd=str(build),argv=argv,inputs=inputs),indent=2)+'\n')
        print('EXIT_ZERO_MINIMAL_LANE '+mode,flush=True)
        with (lane/'run.log').open('x') as log:
            code = run_process(argv, build, log, LANE_TIMEOUT_SECONDS, SHUTDOWN_GRACE_SECONDS)
            log.write('\nEXIT '+str(code)+'\n')
        sent=[p.read_text().split('\t') for p in audit.glob('*.sent')]
        reports=list((build/'target').glob('**/test-reports/*.xml'))
        raw=(lane/'run.log').read_text()
        valid=code==0 and len(sent)==EVENT_COUNT and all(len(row)==4 for row in sent) and sorted((r[0],r[1],r[2]) for r in sent)==sorted(('fixture.SuccessSuite',str(i),'Success') for i in range(1,EVENT_COUNT+1)) and raw.count('GENERIC_EXIT_ZERO_COMPLETE result=Passed')==1
        parents=re.findall(r'^GENERIC_EXIT_ZERO_PARENT pid=(\d+)$',raw,re.M)
        children={row[3] for row in sent if len(row)==4}
        valid=valid and len(parents)==len(children)==1 and not children.intersection(parents)
        if mode=='halt':
            halts=re.findall(r'^GENERIC_EXIT_ZERO_HALT pid=(\d+) sent=15 exit=0$',raw,re.M)
            valid=valid and not reports and len(halts)==1 and set(halts)==children and 'GENERIC_EXIT_ZERO_EVENTS' not in raw
        else:
            valid=valid and len(reports)==1
            if reports:
                xml=ElementTree.parse(reports[0])
                valid=valid and xml.getroot().attrib['tests']==str(EVENT_COUNT) and all(xml.getroot().attrib[k]=='0' for k in ['failures','errors','skipped']) and sorted((n.attrib['classname'],n.attrib['name']) for n in xml.findall('.//testcase'))==sorted(('fixture.SuccessSuite','case '+str(i)) for i in range(1,EVENT_COUNT+1)) and not any(xml.findall('.//'+kind) for kind in ['error','failure','skipped'])
        for row in inputs: assert sha(row['path'])==row['sha256']
        assert sha(__file__)==sha(evidence/'driver.py')
        record=dict(mode=mode,actualExit=code,valid=valid,sentSuccessEvents=len(sent),reports=len(reports),scope='Public generic test-interface only. Sent files follow target EventHandler.handle return; halt has no host group/event/XML receipt. No historical wire trace is captured.')
        (lane/'completion.json').write_text(json.dumps(record,indent=2)+'\n')
        outcomes.append(record)
        print(json.dumps(record),flush=True)
        if not valid: break
    valid=len(outcomes)==2 and all(row['valid'] for row in outcomes)
    (evidence/'completion.json').write_text(json.dumps(dict(exit=0 if valid else 1,outcomes=outcomes,scope='Driver0 requires reproduction of stock SBT2 exit-zero false success. No distage dependency/plugin or SDK patch.'),indent=2)+'\n')
    raise SystemExit(0 if valid else 1)
if __name__ == '__main__':
    main()
