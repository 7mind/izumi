#!/usr/bin/env python3
from pathlib import Path
import argparse,hashlib,json,os,re,shutil,signal,subprocess,time
from xml.etree import ElementTree
ROOT=Path(__file__).resolve().parents[2]
TIMEOUT_SECONDS=180
GRACE_SECONDS=10
HOLD_WAIT_SECONDS=120
HOLD_WINDOW_SECONDS=5
EXPECTED_CONTROL_COUNT=2
SOURCE=r'''package fixture
import sbt.testing.{Event,EventHandler,Fingerprint,Framework,Logger,OptionalThrowable,Runner,Selector,Status,SubclassFingerprint,Task,TaskDef,TestSelector}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files,Paths,StandardOpenOption}
abstract class BatchSpec
final class SuiteA extends BatchSpec
final class SuiteB extends BatchSpec
final class BatchFramework extends Framework {
  private val HostWaitSeconds = 20L
  private val PollMillis = 5L
  private val fp = new SubclassFingerprint {
    override def isModule(): Boolean = false
    override def superclassName(): String = classOf[BatchSpec].getName
    override def requireNoArgConstructor(): Boolean = true
  }
  override def name(): String = "generic-held-batch"
  override def fingerprints(): Array[Fingerprint] = Array(fp)
  override def runner(arguments: Array[String], remoteArguments: Array[String], loader: ClassLoader): Runner = new Runner {
    override def args(): Array[String] = arguments.clone()
    override def remoteArgs(): Array[String] = remoteArguments.clone()
    override def tasks(definitions: Array[TaskDef]): Array[Task] = definitions.sortBy(_.fullyQualifiedName()).map { definition => new Task {
      override def taskDef(): TaskDef = definition
      override def tags(): Array[String] = Array.empty
      override def execute(handler: EventHandler, loggers: Array[Logger]): Array[Task] = {
        val audit = Paths.get(sys.props("fixture.audit-root"))
        (1 to 3).foreach { index =>
          val text = definition.fullyQualifiedName() + "\t" + index + "\t" + ProcessHandle.current().pid()
          val written = Files.write(audit.resolve(definition.fullyQualifiedName() + "-" + index + ".body"),text.getBytes(StandardCharsets.UTF_8),StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)
          require(Files.isRegularFile(written),"Body not recorded")
          handler.handle(new Event {
            override def fullyQualifiedName(): String = definition.fullyQualifiedName()
            override def fingerprint(): Fingerprint = definition.fingerprint()
            override def selector(): Selector = new TestSelector("body-" + index)
            override def status(): Status = Status.Success
            override def throwable(): OptionalThrowable = new OptionalThrowable
            override def duration(): Long = 0L
          })
        }
        Array.empty
      }
    }}
    override def done(): String = {
      val audit = Paths.get(sys.props("fixture.audit-root"))
      val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(HostWaitSeconds)
      while (!Files.isRegularFile(audit.resolve("host.first-complete")) && System.nanoTime() < deadline) Thread.sleep(PollMillis)
      require(Files.isRegularFile(audit.resolve("host.first-complete")),"First host batch missing")
      val parent = new String(Files.readAllBytes(audit.resolve("host.parent")),StandardCharsets.UTF_8)
      if (parent != ProcessHandle.current().pid().toString) {
        val written = Files.write(audit.resolve("child.done"),ProcessHandle.current().pid().toString.getBytes(StandardCharsets.UTF_8),StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)
        require(Files.isRegularFile(written),"Child done not recorded")
      }
      ""
    }
  }
}
'''
BUILD=r'''scalaVersion := "3.9.0"
libraryDependencies += "org.scala-sbt" % "test-interface" % "1.0" % Test
scalacOptions ++= Seq("-release:17", "-Ybackend-parallelism", "1")
Test / fork := true
Test / parallelExecution := false
Test / testFrameworks := Seq(new TestFramework("fixture.BatchFramework"))
Test / javaOptions += "-Dfixture.audit-root=" + sys.props("fixture.audit-root")
Test / testListeners += new TestsListener {
  private val audit = file(sys.props("fixture.audit-root"))
  private val HostWaitSeconds = 20L
  private val PollMillis = 5L
  override def doInit(): Unit = IO.write(audit / "host.parent",ProcessHandle.current().pid().toString)
  override def startGroup(name: String): Unit = ()
  override def testEvent(event: TestEvent): Unit = {
    if (sys.props("fixture.mode") == "held" && event.detail.exists(_.fullyQualifiedName() == "fixture.SuiteB")) {
      require(event.detail.size == 3 && event.detail.forall(_.status() == sbt.testing.Status.Success),"Held batch differs")
      IO.write(audit / "host.held",event.detail.head.fullyQualifiedName() + "\t" + event.detail.size)
      val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(HostWaitSeconds)
      while (!(audit / "host.allow").isFile && System.nanoTime() < deadline) Thread.sleep(PollMillis)
      require((audit / "host.allow").isFile,"Held batch not released")
      IO.write(audit / "host.returned",ProcessHandle.current().pid().toString)
    }
  }
  override def endGroup(name: String,cause: Throwable): Unit = throw new IllegalStateException("Unexpected group failure",cause)
  override def endGroup(name: String,result: TestResult): Unit = {
    require(result == TestResult.Passed,"Group result differs")
    IO.write(audit / (if (name == "fixture.SuiteA") "host.first-complete" else "host.second-complete"),result.toString)
  }
  override def doComplete(result: TestResult): Unit = IO.write(audit / "host.completed",result.toString)
}
Test / SELECTED / testResultLogger := {
  val inherited = (Test / SELECTED / testResultLogger).value
  new TestResultLogger {
    override def run(log: sbt.util.Logger,output: Tests.Output,taskName: String): Unit = {
      val audit = file(sys.props("fixture.audit-root"))
      val rows = output.events.toVector.sortBy(_._1).map { case (name,result) => name + "\t" + result.result + "\t" + result.passedCount + "\t" + result.failureCount + "\t" + result.errorCount + "\t" + result.skippedCount }
      IO.write(audit / "host.output",rows.mkString("\n"))
      IO.write(audit / "host.output-state","returned=" + (audit / "host.returned").isFile + "\tsecond=" + (audit / "host.second-complete").isFile + "\tcompleted=" + (audit / "host.completed").isFile)
      require(output.events.keys.toVector.sorted == Vector("fixture.SuiteA","fixture.SuiteB") && output.events.values.forall(r => r.result == TestResult.Passed && r.passedCount == 3 && r.failureCount == 0 && r.errorCount == 0 && r.skippedCount == 0),"GENERIC_HELD_BATCH_OUTPUT_DIFFERS: " + rows.mkString(";"))
      inherited.run(log,output,taskName)
    }
  }
}
'''

def sha(p): return hashlib.sha256(Path(p).read_bytes()).hexdigest()
def main():
    parser=argparse.ArgumentParser();parser.add_argument('--evidence-dir',type=Path,required=True);a=parser.parse_args()
    out=a.evidence_dir.resolve();out.mkdir(exist_ok=False);shutil.copy2(__file__,out/'driver.py');outcomes=[]
    for sdk in ['2.0.9']:
        for mode in ['normal','held']:
            lane=out/('sbt'+sdk+'-'+mode);build=lane/'build';(build/'project').mkdir(parents=True);(build/'src/test/scala').mkdir(parents=True);audit=lane/'audit';audit.mkdir()
            (build/'build.sbt').write_text(BUILD.replace('SELECTED','testSelected'));(build/'project/build.properties').write_text('sbt.version='+sdk+'\n');(build/'src/test/scala/BatchFramework.scala').write_text(SOURCE)
            commands=['testOnly fixture.SuiteA fixture.SuiteB']
            if sdk=='2.0.9':commands.insert(0,'set Global / localCacheDirectory := file("'+str(lane/'local-cache')+'")')
            shell='task_sdk="$1"; shift; exec sbt --server --sbt-version "$task_sdk" -java-home "$JDK21" -batch -J-Xmx6G "$@"'
            argv=['direnv','exec',str(ROOT),'sh','-c',shell,'generic-held-batch',sdk,'-Dfixture.mode='+mode,'-Dfixture.audit-root='+str(audit),*commands]
            inputs=[dict(path=str(p),sha256=sha(p)) for p in sorted(build.rglob('*')) if p.is_file()];(lane/'commands.json').write_text(json.dumps(dict(cwd=str(build),argv=argv,inputs=inputs),indent=2)+'\n')
            print('GENERIC_HELD_BATCH_LANE '+sdk+' '+mode,flush=True);observation=None
            with (lane/'run.log').open('x') as log:
                process=subprocess.Popen(argv,cwd=build,stdout=log,stderr=subprocess.STDOUT,start_new_session=True)
                if mode=='held':
                    deadline=time.monotonic()+HOLD_WAIT_SECONDS
                    while process.poll() is None and time.monotonic()<deadline:
                        if (audit/'host.held').is_file() and (audit/'child.done').is_file():
                            window=time.monotonic()+HOLD_WINDOW_SECONDS
                            while time.monotonic()<window and process.poll() is None and not (audit/'host.output').exists():time.sleep(0.01)
                            observation=dict(held=(audit/'host.held').read_text(),returned=(audit/'host.returned').exists(),secondComplete=(audit/'host.second-complete').exists(),childDone=(audit/'child.done').read_text(),parent=(audit/'host.parent').read_text(),bodies=[p.read_text() for p in sorted(audit.glob('*.body'))],outputBeforeAllow=(audit/'host.output').read_text() if (audit/'host.output').is_file() else None)
                            (lane/'held-observation.json').write_text(json.dumps(observation,indent=2)+'\n')
                            with (audit/'host.allow').open('x') as f:f.write('release after frozen observation\n')
                            break
                        time.sleep(0.01)
                try:actual=process.wait(timeout=TIMEOUT_SECONDS)
                except subprocess.TimeoutExpired:
                    os.killpg(process.pid,signal.SIGTERM)
                    try:process.wait(timeout=GRACE_SECONDS)
                    except subprocess.TimeoutExpired:os.killpg(process.pid,signal.SIGKILL);process.wait()
                    actual=124
                log.write('\nEXIT '+str(actual)+'\n')
            raw=(lane/'run.log').read_text();bodies=[p.read_text().split('\t') for p in sorted(audit.glob('*.body'))];parent=(audit/'host.parent').read_text() if (audit/'host.parent').is_file() else None;child=(audit/'child.done').read_text() if (audit/'child.done').is_file() else None
            reports=[];reported=[]
            for p in build.rglob('test-reports/*.xml'):
                xml=ElementTree.parse(p);reports.append(dict(path=str(p),summary=xml.getroot().attrib));reported.extend((n.attrib['classname'],n.attrib['name']) for n in xml.findall('.//testcase'))
            output=(audit/'host.output').read_text() if (audit/'host.output').is_file() else None;state=(audit/'host.output-state').read_text() if (audit/'host.output-state').is_file() else None
            expected=[('fixture.Suite'+letter,str(i)) for letter in 'AB' for i in range(1,4)];full='\n'.join('fixture.Suite'+letter+'\tPassed\t3\t0\t0\t0' for letter in 'AB')
            valid=sorted((r[0],r[1]) for r in bodies)==expected and child is not None and parent is not None and child!=parent and {r[2] for r in bodies}=={child} and len(reports)==2 and sorted(reported)==[(s,'body-'+i) for s,i in expected] and all(row['summary']['tests']=='3' and all(row['summary'][k]=='0' for k in ['errors','failures','skipped']) for row in reports)
            if mode=='held':valid=valid and observation is not None and observation['held']=='fixture.SuiteB\t3' and not observation['returned'] and not observation['secondComplete'] and len(observation['bodies'])==6 and state=='returned=true\tsecond=true\tcompleted=true'
            if sdk=='2.0.9' and mode=='held':valid=valid and actual==1 and output=='fixture.SuiteA\tPassed\t3\t0\t0\t0' and 'GENERIC_HELD_BATCH_OUTPUT_DIFFERS' in raw
            else:valid=valid and actual==0 and output==full
            for row in inputs:assert sha(row['path'])==row['sha256']
            assert sha(__file__)==sha(out/'driver.py')
            row=dict(sbt=sdk,mode=mode,actualExit=actual,valid=valid,bodies=len(bodies),xmlCases=len(reported),publicOutput=output,resultLoggerState=state,parentPid=parent,childPid=child,reports=reports,observation=observation);(lane/'completion.json').write_text(json.dumps(row,indent=2)+'\n');outcomes.append(row);print(json.dumps({k:v for k,v in row.items() if k not in ['reports','observation']}),flush=True)
            if not valid:break
        if not outcomes[-1]['valid']:break
    valid=len(outcomes)==EXPECTED_CONTROL_COUNT and all(row['valid'] for row in outcomes);terminal=dict(exit=0 if valid else 1,outcomes=outcomes,scope='Expected SDK-defect reproduction; public test-interface/listener/logger only, one framework and two three-body suites, no izumi/plugin/private SDK changes. Child done waits only for first host group. Driver0 requires held SDK2 incomplete Output after second group and doComplete return, not product acceptance.')
    (out/'completion.json').write_text(json.dumps(terminal,indent=2)+'\n');print(json.dumps(dict(exit=terminal['exit'],completionSha256=sha(out/'completion.json'))));raise SystemExit(terminal['exit'])
if __name__=='__main__':main()
