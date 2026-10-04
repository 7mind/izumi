#!/usr/bin/env python3
from pathlib import Path
import argparse,hashlib,importlib.util,json,os,re,shutil,signal,subprocess,time
from xml.etree import ElementTree
ROOT=Path(__file__).resolve().parents[2]
TIMEOUT_SECONDS=240
HOLD_WAIT_SECONDS=120
HOLD_WINDOW_SECONDS=5
GRACE_SECONDS=10
SETTINGS=r'''
val completionFramework = new TestFramework("izumi.fixtures.host.CompletionAuditFramework")
Test / testFrameworks := ORDER
Test / SELECTED / testExecution := UNCACHED {
  val inherited = (Test / SELECTED / testExecution).value
  val original = new TestFramework("izumi.distage.testkit.runner.bootstrap.Framework")
  inherited.copy(options = inherited.options.map {
    case Tests.Argument(Some(owner), values) if owner == original => Tests.Argument(completionFramework, values: _*)
    case other => other
  })
}
Test / SELECTED / testResultLogger := {
  val inherited = (Test / SELECTED / testResultLogger).value
  new TestResultLogger {
    override def run(log: sbt.util.Logger, output: Tests.Output, taskName: String): Unit = {
      val rows = output.events.toVector.sortBy(_._1).map { case (name, result) => name + "\t" + result.result + "\t" + result.passedCount + "\t" + result.failureCount + "\t" + result.errorCount + "\t" + result.skippedCount }
      IO.write(file(sys.props("izumi.fixture.audit-root")) / "host.result-counts", rows.mkString("\n"))
      val audit = file(sys.props("izumi.fixture.audit-root"))
      IO.write(audit / "host.output-observation", "foreignReturned=" + (audit / "host.foreign-returned").isFile + "\tdoComplete=" + (audit / "host.completed").isFile)
      val expected = ("ABCDE".map(letter => "izumi.fixtures.host.Suite" + letter) :+ "izumi.fixtures.host.ForeignSuite").sorted
      require(output.events.keys.toVector.sorted == expected && output.events.values.forall(result => result.result == TestResult.Passed && result.passedCount == 3 && result.failureCount == 0 && result.errorCount == 0 && result.skippedCount == 0), "MIXED_FORK_RESULT_SET_DIFFERS: " + rows.mkString(";"))
      inherited.run(log, output, taskName)
    }
  }
}
Test / testListeners += {
  val receiptParent = target.value / "distage-fork-receipts"
  val modeFile = baseDirectory.value / "target/foreign-hold-mode.txt"
  val endedGroups = scala.collection.mutable.Set.empty[String]
  val expectedGroups = ("ABCDE".map(letter => "izumi.fixtures.host.Suite" + letter) :+ "izumi.fixtures.host.ForeignSuite").toSet
  val HostWaitSeconds = 20L
  val PollMillis = 5L
  new TestsListener {
    override def doInit(): Unit = {
      val dirs = (receiptParent * "command-*").get().filter(_.isDirectory)
      require(dirs.size == 1, "Expected one mixed command directory")
      IO.write(file(sys.props("izumi.fixture.audit-root")) / "host-command.fork-dir", dirs.head.getCanonicalPath)
      IO.write(file(sys.props("izumi.fixture.audit-root")) / "host.parent-pid", ProcessHandle.current().pid().toString)
    }
    override def startGroup(name: String): Unit = ()
    override def testEvent(event: TestEvent): Unit = {
      val audit = file(sys.props("izumi.fixture.audit-root"))
      if (IO.read(modeFile) == "held" && event.detail.exists(_.fullyQualifiedName() == "izumi.fixtures.host.ForeignSuite")) {
        require(event.detail.size == 3 && event.detail.forall(e => e.fullyQualifiedName() == "izumi.fixtures.host.ForeignSuite" && e.status() == sbt.testing.Status.Success), "Held foreign event batch differs")
        IO.write(audit / "host.foreign-held", "pid=" + ProcessHandle.current().pid() + "\tevents=" + event.detail.size)
        val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(20L)
        while (!(audit / "host.foreign-allow").exists() && System.nanoTime() < deadline) Thread.sleep(5L)
        require((audit / "host.foreign-allow").isFile,"Held foreign batch was not released")
        IO.write(audit / "host.foreign-returned", "pid=" + ProcessHandle.current().pid())
      }
    }
    override def endGroup(name: String, cause: Throwable): Unit = throw new IllegalStateException("Mixed group failed: " + name,cause)
    override def endGroup(name: String, result: TestResult): Unit = synchronized {
      val audit = file(sys.props("izumi.fixture.audit-root"))
      IO.write(audit / ("host.end-" + name), result.toString)
      require(result == TestResult.Passed,"Mixed group failed")
      require(expectedGroups.contains(name) && !endedGroups.contains(name),"Global group completion duplicated or unknown")
      endedGroups += name
      if (endedGroups.toSet == expectedGroups) {
        IO.write(audit / "host.global-groups",endedGroups.toVector.sorted.mkString(","))
        val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(HostWaitSeconds)
        while (!(audit / "target.shutdown-ready").isFile && System.nanoTime() < deadline) Thread.sleep(PollMillis)
        require((audit / "target.shutdown-ready").isFile,"Global child acknowledgement missing")
        IO.write(audit / "host.global-returned",ProcessHandle.current().pid().toString)
      }
    }
    override def doComplete(result: TestResult): Unit = {
      IO.write(file(sys.props("izumi.fixture.audit-root")) / "host.completed",result.toString)
      require(result == TestResult.Passed,"Mixed command failed")
    }
  }
}
val changeForeignMode = inputKey[Unit]("Select ordinary or held foreign notification delivery")
changeForeignMode := {
  val parsed = spaceDelimited("mode").parsed
  require(parsed.size == 1 && Set("normal", "held").contains(parsed.head), "Invalid foreign delivery mode")
  IO.write(baseDirectory.value / "target/foreign-hold-mode.txt", parsed.head)
  if (parsed.head == "held") IO.delete((Test / target).value / "test-reports")
}
val captureMixedFixture = inputKey[Unit]("Freeze mixed execution/output/report identities")
captureMixedFixture := {
  val parsed: Seq[String] = spaceDelimited("case").parsed;
  UNCACHED {
    require(parsed.size == 1,"Expected one mixed case")
    val capture = file(sys.props("izumi.fixture.captures")) / parsed.head
    require(!capture.exists(),"Mixed capture must be new")
    IO.copyDirectory(file(sys.props("izumi.fixture.audit-root")), capture / "body-audit")
    IO.copyDirectory((Test / target).value / "test-reports", capture / "test-reports")
    val parent = (Test / target).value / "distage-fork-receipts"
    require(parent.exists() && (parent * "*").get().isEmpty,"Mixed receipt directory survived")
    streams.value.log.info("MIXED_FORK_CAPTURE_OK case=" + parsed.head + " cleaned=1")
  }
}
'''
def sha(p): return hashlib.sha256(Path(p).read_bytes()).hexdigest()
def main():
    parser=argparse.ArgumentParser(); parser.add_argument('--evidence-dir',required=True,type=Path); parser.add_argument('--artifact-version',required=True); parser.add_argument('--sbt-version',nargs='+',required=True,choices=['2.0.9']); parser.add_argument('--scala-version',nargs='+',required=True,choices=['3.9.0','2.13.18','2.12.21']); parser.add_argument('--framework-order',required=True,choices=['own-first','foreign-first']); a=parser.parse_args()
    out=a.evidence_dir.resolve(); out.mkdir(exist_ok=False); shutil.copy2(__file__,out/'driver.py')
    helper=ROOT/'test-fixtures/host-sharing-consumer/verify-held-forks.py'
    spec=importlib.util.spec_from_file_location('held_source',helper); module=importlib.util.module_from_spec(spec); spec.loader.exec_module(module)
    source=module.SOURCE.replace('HeldDeliveryFramework','CompletionAuditFramework').replace('distage-held-delivery-control','distage-mixed-completion-control')
    hook='\n    if (child) {\n      val directory = Paths.get(sys.props("izumi.fixture.audit-root"))\n      val selectedGroups = ("ABCDE".map(letter => "izumi.fixtures.host.Suite" + letter) :+ "izumi.fixtures.host.ForeignSuite").sorted.mkString(",")\n      val HostWaitSeconds = 20L\n      val PollMillis = 5L\n      val shutdown = new Thread(new Runnable {\n        override def run(): Unit = {\n          val pid = ProcessHandle.current().pid().toString\n          val entered = Files.write(directory.resolve("target.shutdown-enter"),pid.getBytes(StandardCharsets.UTF_8),StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)\n          require(Files.isRegularFile(entered),"Global shutdown entry missing")\n          val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(HostWaitSeconds)\n          val receipt = directory.resolve("host.global-groups")\n          while (!Files.isRegularFile(receipt) && System.nanoTime() < deadline) Thread.sleep(PollMillis)\n          require(Files.isRegularFile(receipt),"Global host group acknowledgement missing")\n          require(new String(Files.readAllBytes(receipt),StandardCharsets.UTF_8) == selectedGroups,"Global acknowledged group set differs")\n          val ready = Files.write(directory.resolve("target.shutdown-ready"),pid.getBytes(StandardCharsets.UTF_8),StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)\n          require(Files.isRegularFile(ready),"Global shutdown acknowledgement missing")\n        }\n      }, "fixture-owned-global-fork-shutdown")\n      Runtime.getRuntime.addShutdownHook(shutdown)\n    }\n'
    source=source.replace('    new Runner {',hook+'    new Runner {')
    (out/'completion-framework-source.scala').write_text(source); shutil.copy2(helper,out/'source-helper.py')
    fixture=ROOT/'test-fixtures/host-sharing-consumer'; original=[fixture/'build.sbt',*sorted((fixture/'src').rglob('*.scala'))]; sources=[dict(path=str(p),sha256=sha(p)) for p in original]
    (out/'inputs.json').write_text(json.dumps(dict(sources=sources,sourceHelper=str(helper),sourceHelperSha256=sha(helper)),indent=2)+'\n')
    owned=['izumi.fixtures.host.Suite'+letter for letter in 'ABCDE']; expected=sorted(owned+['izumi.fixtures.host.ForeignSuite']); outcomes=[]; resources=set(); directories=set()
    for sdk in a.sbt_version:
        for scala in a.scala_version:
            lane=out/('sbt'+sdk+'-scala'+scala); build=lane/'build'; build.mkdir(parents=True)
            for p in original:
                dest=build/p.relative_to(fixture); dest.parent.mkdir(parents=True,exist_ok=True); value=p.read_text()
                if p.name=='build.sbt':
                    start=value.index('Test / testFrameworks :='); end=value.index('Test / javaOptions +=',start); value=value[:start]+value[end:]
                    value+='\nlazy val mixedConsumer = project.in(file(".")).enablePlugins(izumi.distage.sbt.DistageTestkitPlugin)\nTest / fork := true\n'+SETTINGS.replace('ORDER','Seq(completionFramework, new TestFramework("izumi.fixtures.host.ForeignFramework"))' if a.framework_order=='own-first' else 'Seq(new TestFramework("izumi.fixtures.host.ForeignFramework"), completionFramework)').replace('SELECTED','testSelected' if sdk=='2.0.9' else 'testOnly').replace('UNCACHED','Def.uncached' if sdk=='2.0.9' else '')
                elif p.name in ['SuiteA.scala','SuiteB.scala']: value=value.replace('extends PlainFixtureSuite','extends DIFixtureSuite')
                dest.write_text(value)
            (build/'src/test/scala/izumi/fixtures/host/CompletionAuditFramework.scala').write_text(source)
            project=build/'project'; project.mkdir(); (project/'build.properties').write_text('sbt.version='+sdk+'\n'); (project/'plugins.sbt').write_text('addSbtPlugin("io.7mind.izumi" % "sbt-distage-testkit" % "'+a.artifact_version+'")\n')
            selected='testOnly '+' '.join(expected); commands=['changeForeignMode normal','prepareFixture baseline',selected,'captureMixedFixture baseline','changeForeignMode held','prepareFixture held',selected,'captureMixedFixture held','show Test / dependencyClasspath','show Test / fullClasspath']
            if sdk=='2.0.9': commands.insert(0,'set Global / localCacheDirectory := file("'+str(lane/'local-cache')+'")')
            shell='task_sdk="$1"; shift; exec sbt --server --sbt-version "$task_sdk" -java-home "$JDK21" -batch -J-Xmx6G "$@"'
            argv=['direnv','exec',str(ROOT),'sh','-c',shell,'mixed-fork',sdk,'-Dizumi.fixture.scala-version='+scala,'-Dizumi.fixture.version='+a.artifact_version,'-Dizumi.fixture.audit-root='+str(build/'target/body-audit'),'-Dizumi.fixture.captures='+str(lane/'cases'),*commands]
            generated=[dict(path=str(p),sha256=sha(p)) for p in sorted(build.rglob('*')) if p.is_file()]; (lane/'commands.json').write_text(json.dumps(dict(cwd=str(build),argv=argv,inputs=generated),indent=2)+'\n')
            print('HELD_FOREIGN_LANE_START '+sdk+' '+scala+' '+a.framework_order,flush=True)
            audit=build/'target/body-audit'; observation=None
            with (lane/'run.log').open('x') as log:
                process=subprocess.Popen(argv,cwd=build,stdout=log,stderr=subprocess.STDOUT,start_new_session=True)
                deadline=time.monotonic()+HOLD_WAIT_SECONDS
                while process.poll() is None and time.monotonic()<deadline:
                    if (audit/'host.foreign-held').is_file() and (audit/'target.done-enter').is_file():
                        window=time.monotonic()+HOLD_WINDOW_SECONDS
                        while time.monotonic()<window and not (audit/'host.result-counts').exists() and process.poll() is None: time.sleep(0.01)
                        output=(audit/'host.result-counts').read_text() if (audit/'host.result-counts').is_file() else None
                        observedDirectory=Path((audit/'host-command.fork-dir').read_text())
                        observation=dict(host=(audit/'host.foreign-held').read_text(),child=(audit/'target.done-enter').read_text(),doneFinished=(audit/'target.done-finish').exists(),shutdownEntered=(audit/'target.shutdown-enter').exists(),shutdownReady=(audit/'target.shutdown-ready').exists(),globalHostAck=(audit/'host.global-groups').exists(),foreignReturned=(audit/'host.foreign-returned').exists(),physical=[p.read_text() for p in sorted(audit.glob('*.body'))],acquired=[p.read_text() for p in audit.glob('*.acquire')],released=[p.read_text() for p in audit.glob('*.release')],receiptDirectory=str(observedDirectory),receiptFiles=[dict(name=p.name,text=p.read_text()) for p in sorted(observedDirectory.iterdir())] if observedDirectory.is_dir() else None,parentPid=process.pid,parentOutputBeforeAllow=output,processExitBeforeAllow=process.poll())
                        childPid=int(observation['child'].split('\t')[0].removeprefix('pid='))
                        try:os.kill(childPid,0);observation['childAlive']=True
                        except ProcessLookupError:observation['childAlive']=False
                        observation['validGlobalHold']=observation['childAlive'] and not observation['shutdownReady'] and not observation['globalHostAck'] and observation['doneFinished']==(a.framework_order=='own-first') and observation['shutdownEntered']==(a.framework_order=='own-first')
                        (lane/'held-observation.json').write_text(json.dumps(observation,indent=2)+'\n')
                        with (audit/'host.foreign-allow').open('x') as f: f.write('release after frozen foreign observation\n')
                        break
                    time.sleep(0.01)
                try: actual=process.wait(timeout=TIMEOUT_SECONDS)
                except subprocess.TimeoutExpired:
                    os.killpg(process.pid,signal.SIGTERM)
                    try: process.wait(timeout=GRACE_SECONDS)
                    except subprocess.TimeoutExpired: os.killpg(process.pid,signal.SIGKILL); process.wait()
                    actual=124
                log.write('\nEXIT '+str(actual)+'\n')
            failures=[]; cases=[]
            baseline=lane/'cases/baseline/body-audit'
            baselineOutput=(baseline/'host.result-counts').read_text() if (baseline/'host.result-counts').is_file() else None
            baselineBodies=[p.read_text().split('\t') for p in baseline.glob('*.body')]
            if sorted((r[0],r[1]) for r in baselineBodies)!=[(s,str(i)) for s in expected for i in range(1,4)] or baselineOutput!='\n'.join('\t'.join([s,'Passed','3','0','0','0']) for s in expected): failures.append('Normal mixed baseline differs')
            if observation is None: failures.append('Foreign held callback / own done entry not observed')
            else:
                physical=[r.split('\t') for r in observation['physical']]
                if sorted((r[0],r[1]) for r in physical)!=[(s,str(i)) for s in expected for i in range(1,4)] or len(observation['acquired'])!=1 or observation['acquired']!=observation['released'] or observation['foreignReturned']: failures.append('Held foreign bodies/lifetime/gate state differs')
            output=(audit/'host.result-counts').read_text() if (audit/'host.result-counts').is_file() else None
            shutdownChecks=[]
            for caseName in ['baseline','held']:
                caseAudit=lane/'cases/baseline/body-audit' if caseName=='baseline' else audit
                required=[caseAudit/n for n in ['target.shutdown-enter','target.shutdown-ready','host.global-groups','host.global-returned']]
                validShutdown=all(p.is_file() for p in required)
                if validShutdown:
                    validShutdown=required[0].read_text()==required[1].read_text() and required[2].read_text()==','.join(expected) and required[3].read_text()==(caseAudit/'host.parent-pid').read_text()
                if not validShutdown:failures.append('Global public shutdown/group acknowledgement differs: '+caseName)
                shutdownChecks.append(dict(case=caseName,valid=validShutdown))
            xml=[]
            for p in sorted(build.rglob('test-reports/*.xml')):
                node=ElementTree.parse(p); xml.append(dict(path=str(p),summary=node.getroot().attrib,cases=[n.attrib for n in node.findall('.//testcase')]))
            reported=sorted((n['classname'],n['name']) for row in xml for n in row['cases'])
            expectedReports=sorted((s,'equal display name should '+leaf) for s in expected for leaf in ['first','second','third'])
            if reported!=expectedReports or len(xml)!=6 or any(row['summary']['tests']!='3' or any(row['summary'][key]!='0' for key in ['errors','failures','skipped']) for row in xml): failures.append('Fresh held XML identities/outcomes differ')
            callback=(audit/'host.foreign-returned').read_text() if (audit/'host.foreign-returned').is_file() else None
            resultObservation=(audit/'host.output-observation').read_text() if (audit/'host.output-observation').is_file() else None
            reproduced=observation is not None and output=='\n'.join('\t'.join([s,'Passed','3','0','0','0']) for s in owned) and actual==1 and callback is not None and resultObservation=='foreignReturned=true\tdoComplete=true' and 'MIXED_FORK_RESULT_SET_DIFFERS' in (lane/'run.log').read_text()
            passed=actual==0 and output=='\n'.join('\t'.join([s,'Passed','3','0','0','0']) for s in expected) and callback is not None and resultObservation=='foreignReturned=true\tdoComplete=true'
            if not passed: failures.append('Prototype global handshake failed exact SDK-output reconciliation')
            if observation is None or not observation['validGlobalHold']:failures.append('Prototype global held state differs')
            for row in sources+generated: assert sha(row['path'])==row['sha256']
            assert sha(helper)==sha(out/'source-helper.py') and sha(__file__)==sha(out/'driver.py')
            row=dict(sbt=sdk,scala=scala,frameworkOrder=a.framework_order,actualExit=actual,validationFailures=failures,defectReproduced=reproduced,baselineBodies=len(baselineBodies),heldOutput=output,heldXml=xml,callbackReturned=callback,resultLoggerObservation=resultObservation,exactHeldSuccess=passed,shutdownChecks=shutdownChecks,observation=observation); (lane/'completion.json').write_text(json.dumps(row,indent=2)+'\n'); outcomes.append(row); print(json.dumps({k:v for k,v in row.items() if k not in ['heldXml','observation']}),flush=True)
            if failures: break
        if outcomes[-1]['validationFailures']: break
    valid=len(outcomes)==len(a.sbt_version)*len(a.scala_version) and all(not r['validationFailures'] for r in outcomes)
    terminal=dict(exit=0 if valid else 1,outcomes=outcomes,scope='Prototype public session-owned JVM shutdown hook and last-selected-group acknowledgement for SDK2 only; six-group metadata is fixed fixture input and no production source changes. Diagnostic held foreign host callback with unchanged original framework. Driver0 validates control capture, not product acceptance; defectReproduced requires exact five-owned-success Output versus all eighteen fresh XML identities after the foreign callback and doComplete return, plus the exact public result-set failure. No production correction.')
    (out/'completion.json').write_text(json.dumps(terminal,indent=2)+'\n'); print(json.dumps(dict(exit=terminal['exit'],completionSha256=sha(out/'completion.json'))),flush=True); raise SystemExit(terminal['exit'])
if __name__=='__main__': main()
