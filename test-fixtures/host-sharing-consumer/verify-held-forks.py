#!/usr/bin/env python3
from pathlib import Path
import argparse, hashlib, json, os, re, shutil, signal, subprocess, time
from xml.etree import ElementTree
ROOT=Path(__file__).resolve().parents[2]
LANE_TIMEOUT_SECONDS=240
HOLD_OBSERVATION_SECONDS=120
GRACE_SECONDS=10
SOURCE=r'''package izumi.fixtures.host
import izumi.distage.testkit.protocol.ForkReceiptArguments
import sbt.testing.{Fingerprint, Framework, Runner, Task, TaskDef}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths, StandardOpenOption}
final class HeldDeliveryFramework extends Framework {
  private val framework = new izumi.distage.testkit.runner.bootstrap.Framework
  override def name(): String = "distage-held-delivery-control"
  override def fingerprints(): Array[Fingerprint] = framework.fingerprints()
  override def runner(arguments: Array[String], remoteArguments: Array[String], loader: ClassLoader): Runner = {
    val child = ForkReceiptArguments.parse(arguments.toVector, remoteArguments.toVector).forked
    val delegate = framework.runner(arguments.clone(), remoteArguments.clone(), loader)
    new Runner {
      private var selected = Vector.empty[String]
      override def args(): Array[String] = delegate.args()
      override def remoteArgs(): Array[String] = delegate.remoteArgs()
      override def tasks(definitions: Array[TaskDef]): Array[Task] = synchronized {
        selected ++= definitions.toVector.map(_.fullyQualifiedName())
        delegate.tasks(definitions)
      }
      override def done(): String = {
        val directory = Paths.get(sys.props("izumi.fixture.audit-root"))
        val receipt = "pid=" + ProcessHandle.current().pid() + "\tsuites=" + selected.sorted.mkString(",")
        if (child) {
          val entered = Files.write(directory.resolve("target.done-enter"), receipt.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
          require(Files.isRegularFile(entered), "Target done entry was not recorded")
        }
        val result = delegate.done()
        if (child) {
          val finished = Files.write(directory.resolve("target.done-finish"), receipt.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
          require(Files.isRegularFile(finished), "Target done completion was not recorded")
        }
        result
      }
    }
  }
}
'''
SETTINGS=r'''
val changeHoldMode = inputKey[Unit]("Select held or ordinary host delivery without reapplying settings")
changeHoldMode := {
  val parsed = spaceDelimited("mode").parsed
  require(parsed.size == 1 && Set("held", "normal").contains(parsed.head), "Invalid host hold mode")
  IO.write(baseDirectory.value / "target/host-hold-mode.txt", parsed.head)
  streams.value.log.info("HELD_FORK_MODE mode=" + parsed.head)
}
val captureHeldFixture = inputKey[Unit]("Freeze physical audit and explicitly scoped reports for the independent driver oracle")
captureHeldFixture := {
  val parsed: Seq[String] = spaceDelimited("case").parsed;
  UNCACHED {
    require(parsed.size == 1, "Expected one held fixture case")
    val audit = file(sys.props("izumi.fixture.audit-root"))
    val capture = file(sys.props("izumi.fixture.captures")) / parsed.head
    require(!capture.exists(), "Held capture must be new")
    IO.copyDirectory(audit, capture / "body-audit")
    IO.copyDirectory((Test / target).value / "test-reports", capture / "test-reports")
    streams.value.log.info("HELD_FORK_CAPTURE_OK case=" + parsed.head)
  }
}
val heldFramework = new TestFramework("izumi.fixtures.host.HeldDeliveryFramework")
Test / testFrameworks := Seq(heldFramework)
Test / SELECTED / testExecution := UNCACHED {
  val inherited = (Test / SELECTED / testExecution).value
  val original = new TestFramework("izumi.distage.testkit.runner.bootstrap.Framework")
  inherited.copy(options = inherited.options.map {
    case Tests.Argument(Some(owner), values) if owner == original => Tests.Argument(heldFramework, values: _*)
    case other => other
  })
}
Test / testListeners += {
  val fixtureReceiptParent = target.value / "distage-fork-receipts"
  val held = new java.util.concurrent.atomic.AtomicBoolean(false)
  val fixtureHoldMode = baseDirectory.value / "target/host-hold-mode.txt"
  new TestsListener {
    override def doInit(): Unit = {
      val directories = (fixtureReceiptParent * "command-*").get().filter(_.isDirectory)
      require(directories.size == 1, "Expected one owned command directory")
      IO.write(file(sys.props("izumi.fixture.audit-root")) / "host-command.fork-dir", directories.head.getCanonicalPath)
    }
    override def startGroup(name: String): Unit = ()
    override def testEvent(event: TestEvent): Unit = {
      if (IO.read(fixtureHoldMode) == "held" && held.compareAndSet(false, true)) {
        val audit = file(sys.props("izumi.fixture.audit-root"))
        require(event.detail.size == 3 && event.detail.forall(_.status() == sbt.testing.Status.Success), "Held batch differs")
        IO.write(audit / "host.held", "pid=" + ProcessHandle.current().pid() + "\tsuite=" + event.detail.head.fullyQualifiedName() + "\tevents=" + event.detail.size)
        val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(20L)
        while (!(audit / "host.allow").exists() && System.nanoTime() < deadline) Thread.sleep(5L)
        require((audit / "host.allow").isFile, "Held host batch was not released")
        IO.write(audit / "host.returned", "pid=" + ProcessHandle.current().pid())
      }
    }
    override def endGroup(name: String, cause: Throwable): Unit = throw new IllegalStateException("Unexpected held fixture group failure: " + name, cause)
    override def endGroup(name: String, result: TestResult): Unit = require(result == TestResult.Passed, "Held fixture group failed")
    override def doComplete(result: TestResult): Unit = {
      require(result == TestResult.Passed, "Held fixture overall result differs")
      println("HELD_FORK_HOST_COMPLETE pid=" + ProcessHandle.current().pid())
    }
  }
}
val verifyHeldCleanup = taskKey[Unit]("Check receipt directory removal after held host delivery")
verifyHeldCleanup := UNCACHED {
  val parent = (Test / target).value / "distage-fork-receipts"
  require(parent.exists() && (parent * "*").get().isEmpty, "Held command receipt directory survived completion")
  streams.value.log.info("HELD_FORK_CLEANUP_OK parent=" + parent.getCanonicalPath)
}
'''
def sha(p): return hashlib.sha256(Path(p).read_bytes()).hexdigest()
def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--evidence-dir',required=True,type=Path)
    parser.add_argument('--sbt-version',required=True,nargs='+',choices=['2.0.9','1.13.0'])
    parser.add_argument('--scala-version',required=True,nargs='+',choices=['3.9.0','2.13.18','2.12.21'])
    parser.add_argument('--artifact-version',required=True)
    args=parser.parse_args(); evidence=args.evidence_dir.resolve(); evidence.mkdir(parents=True,exist_ok=False)
    shutil.copy2(__file__,evidence/'driver.py'); fixture=ROOT/'test-fixtures/host-sharing-consumer'
    sources=[fixture/'build.sbt',*sorted((fixture/'src').rglob('*.scala'))]
    original=[dict(path=str(p),sha256=sha(p)) for p in sources]
    (evidence/'inputs.json').write_text(json.dumps(original,indent=2)+'\n')
    outcomes=[]; all_resources=set(); all_directories=set()
    expected_suites=['izumi.fixtures.host.Suite'+letter for letter in 'ABCDE']
    for sdk in args.sbt_version:
        for scala in args.scala_version:
            lane=evidence/('sbt'+sdk+'-scala'+scala); build=lane/'build'; build.mkdir(parents=True)
            for source in sources:
                dest=build/source.relative_to(fixture); dest.parent.mkdir(parents=True,exist_ok=True)
                value=source.read_text()
                if source.name=='build.sbt':
                    start=value.index('Test / testFrameworks :='); end=value.index('Test / javaOptions +=',start); value=value[:start]+value[end:]
                    value+='\nlazy val heldConsumer = project.in(file(".")).enablePlugins(izumi.distage.sbt.DistageTestkitPlugin)\nTest / fork := true\n'
                    value+=SETTINGS.replace('UNCACHED','Def.uncached' if sdk=='2.0.9' else '').replace('SELECTED','testSelected' if sdk=='2.0.9' else 'testOnly')
                elif source.name in ['SuiteA.scala','SuiteB.scala']: value=value.replace('extends PlainFixtureSuite','extends DIFixtureSuite')
                dest.write_text(value)
            (build/'src/test/scala/izumi/fixtures/host/HeldDeliveryFramework.scala').write_text(SOURCE)
            project=build/'project'; project.mkdir(); (project/'build.properties').write_text('sbt.version='+sdk+'\n')
            (project/'plugins.sbt').write_text('addSbtPlugin("io.7mind.izumi" % "sbt-distage-testkit" % "'+args.artifact_version+'")\n')
            audit=build/'target/body-audit'; selected=' '.join(expected_suites)
            commands=['changeHoldMode held','prepareFixture held','testOnly '+selected,'captureHeldFixture held','verifyHeldCleanup','changeHoldMode normal','prepareFixture repeat','testOnly '+selected,'captureHeldFixture repeat','verifyHeldCleanup','show Test / dependencyClasspath','show Test / fullClasspath']
            if sdk=='2.0.9': commands.insert(0,'set Global / localCacheDirectory := file("'+str(lane/'local-cache')+'")')
            shell='task_sdk="$1"; shift; exec sbt --server --sbt-version "$task_sdk" -java-home "$JDK21" -batch -J-Xmx6G "$@"'
            argv=['direnv','exec',str(ROOT),'sh','-c',shell,'held-fork-delivery',sdk,'-Dizumi.fixture.scala-version='+scala,'-Dizumi.fixture.version='+args.artifact_version,'-Dizumi.fixture.audit-root='+str(audit),'-Dizumi.fixture.captures='+str(lane/'cases'),*commands]
            generated=[dict(path=str(p),sha256=sha(p)) for p in sorted(build.rglob('*')) if p.is_file()]
            (lane/'commands.json').write_text(json.dumps(dict(cwd=str(build),argv=argv,inputs=generated),indent=2)+'\n')
            print('HELD_FORK_LANE_START '+sdk+' '+scala,flush=True)
            failures=[]; observation=None
            with (lane/'run.log').open('x') as log:
                process=subprocess.Popen(argv,cwd=build,stdout=log,stderr=subprocess.STDOUT,start_new_session=True)
                deadline=time.monotonic()+HOLD_OBSERVATION_SECONDS
                while process.poll() is None and time.monotonic()<deadline:
                    if (audit/'host.held').is_file() and (audit/'target.done-enter').is_file():
                        host=(audit/'host.held').read_text(); child=(audit/'target.done-enter').read_text()
                        owned=Path((audit/'host-command.fork-dir').read_text())
                        parent_pid=int(host.split('\t')[0].removeprefix('pid=')); child_pid=int(child.split('\t')[0].removeprefix('pid='))
                        alive=False
                        try: os.kill(child_pid,0); alive=True
                        except ProcessLookupError: pass
                        observation=dict(host=host,child=child,childAlive=alive,doneFinished=(audit/'target.done-finish').exists(),bodies=len(list(audit.glob('*.body'))),acquired=[p.read_text() for p in audit.glob('*.acquire')],released=[p.read_text() for p in audit.glob('*.release')],receiptDirectory=str(owned),receiptFiles=[p.name for p in owned.iterdir()] if owned.is_dir() else None,parentProcess=process.pid)
                        valid=parent_pid==process.pid and child_pid!=parent_pid and alive and not observation['doneFinished'] and observation['bodies']==15 and len(observation['acquired'])==1 and observation['acquired']==observation['released'] and observation['receiptFiles']==[] and owned.is_relative_to(build/'target') and child.split('\t')[1]=='suites='+','.join(expected_suites) and host.split('\t')[2]=='events=3'
                        observation['valid']=valid
                        (lane/'held-observation.json').write_text(json.dumps(observation,indent=2)+'\n')
                        if not valid: failures.append('Held target/host/resource/receipt state differs')
                        with (audit/'host.allow').open('x') as release: release.write('release after frozen held observation\n')
                        break
                    time.sleep(0.01)
                if observation is None: failures.append('No held host callback with target done entry was observed')
                try: actual=process.wait(timeout=LANE_TIMEOUT_SECONDS)
                except subprocess.TimeoutExpired:
                    os.killpg(process.pid,signal.SIGTERM)
                    try: process.wait(timeout=GRACE_SECONDS)
                    except subprocess.TimeoutExpired: os.killpg(process.pid,signal.SIGKILL); process.wait()
                    actual=124
                log.write('\nEXIT '+str(actual)+'\n')
            if actual!=0: failures.append('Actual SBT process did not complete both controls')
            raw=(lane/'run.log').read_text()
            if raw.count('HELD_FORK_HOST_COMPLETE pid=')!=2 or raw.count('HELD_FORK_CLEANUP_OK parent=')!=2: failures.append('Host completion/cleanup markers differ')
            cases=[]
            if actual==0:
                for case in ['held','repeat']:
                    captured=lane/'cases'/case; body=captured/'body-audit'
                    resources=[p.read_text() for p in body.glob('*.acquire')]; releases=[p.read_text() for p in body.glob('*.release')]
                    physical=[p.read_text().split('\t') for p in body.glob('*.body')]
                    owned=Path((body/'host-command.fork-dir').read_text())
                    if len(resources)!=1 or resources!=releases or set(resources)&all_resources or {r[2] for r in physical}!=set(resources): failures.append('Fresh paired DI resource differs: '+case)
                    all_resources.update(resources)
                    if str(owned) in all_directories or owned.exists(): failures.append('Fresh receipt directory cleanup differs: '+case)
                    all_directories.add(str(owned))
                    if sorted((r[0],r[1]) for r in physical)!=[(s,str(i)) for s in expected_suites for i in range(1,4)]: failures.append('Physical IDs differ: '+case)
                    entered=(body/'target.done-enter').read_text(); finished=(body/'target.done-finish').read_text()
                    if entered!=finished: failures.append('Target done entry/completion identity differs: '+case)
                    reported=[]; report_paths=sorted((captured/'test-reports').glob('*.xml'))
                    for report in report_paths:
                        xml=ElementTree.parse(report); summary=xml.getroot().attrib
                        if summary['tests']!='3' or any(summary[k]!='0' for k in ['errors','failures','skipped']) or any(xml.findall('.//'+k) for k in ['error','failure','skipped']): failures.append('XML outcome differs: '+case)
                        reported.extend((n.attrib['classname'],n.attrib['name']) for n in xml.findall('.//testcase'))
                    if len(report_paths)!=5 or sorted(reported)!=sorted((s,'equal display name should '+name) for s in expected_suites for name in ['first','second','third']): failures.append('XML IDs differ: '+case)
                    if case=='held' and not (body/'host.returned').is_file(): failures.append('Held host callback did not return')
                    cases.append(dict(case=case,bodies=len(physical),xmlCases=len(reported),resource=resources[0],receiptDirectory=str(owned)))
            for row in original+generated: assert sha(row['path'])==row['sha256']
            assert sha(__file__)==sha(evidence/'driver.py')
            outcome=dict(sbt=sdk,scala=scala,actualExit=actual,validationFailures=failures,cases=cases)
            (lane/'completion.json').write_text(json.dumps(outcome,indent=2)+'\n'); outcomes.append(outcome); print(json.dumps(outcome),flush=True)
            if failures: break
        if outcomes[-1]['validationFailures']: break
    valid=len(outcomes)==len(args.sbt_version)*len(args.scala_version) and all(not row['validationFailures'] for row in outcomes)
    terminal=dict(exit=0 if valid else 1,outcomes=outcomes,resourceLifetimes=len(all_resources),receiptDirectories=len(all_directories),scope='Public forwarding Framework preserves production Runner/Task behavior. Host listener gates one buffered suite batch until target done is entered, DI resources released, no receipt published and target is alive. Release then exact body/XML reconciliation and same-session repeat. No SDK orchestration/private changes or full logger/cancellation/fault acceptance.')
    (evidence/'completion.json').write_text(json.dumps(terminal,indent=2)+'\n'); print(json.dumps(dict(exit=terminal['exit'],completionSha256=sha(evidence/'completion.json'))),flush=True); raise SystemExit(terminal['exit'])
if __name__=='__main__': main()
