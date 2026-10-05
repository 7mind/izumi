#!/usr/bin/env python3
from pathlib import Path
import argparse,hashlib,importlib.util,json,os,re,shutil,signal,subprocess
from xml.etree import ElementTree
ROOT=Path(__file__).resolve().parents[2]
TIMEOUT_SECONDS=240
GRACE_SECONDS=10
SETTINGS=r'''
val completionFramework = new TestFramework("izumi.fixtures.host.CompletionAuditFramework")
Test / testFrameworks := Seq(completionFramework, new TestFramework("izumi.fixtures.host.ForeignFramework"))
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
      val expected = ("ABCDE".map(letter => "izumi.fixtures.host.Suite" + letter) :+ "izumi.fixtures.host.ForeignSuite").sorted
      require(output.events.keys.toVector.sorted == expected && output.events.values.forall(result => result.result == TestResult.Passed && result.passedCount == 3 && result.failureCount == 0 && result.errorCount == 0 && result.skippedCount == 0), "MIXED_FORK_RESULT_SET_DIFFERS: " + rows.mkString(";"))
      inherited.run(log, output, taskName)
    }
  }
}
Test / testListeners += {
  val receiptParent = target.value / "distage-fork-receipts"
  new TestsListener {
    override def doInit(): Unit = {
      val dirs = (receiptParent * "command-*").get().filter(_.isDirectory)
      require(dirs.size == 1, "Expected one mixed command directory")
      IO.write(file(sys.props("izumi.fixture.audit-root")) / "host-command.fork-dir", dirs.head.getCanonicalPath)
      IO.write(file(sys.props("izumi.fixture.audit-root")) / "host.parent-pid", ProcessHandle.current().pid().toString)
    }
    override def startGroup(name: String): Unit = ()
    override def testEvent(event: TestEvent): Unit = ()
    override def endGroup(name: String, cause: Throwable): Unit = throw new IllegalStateException("Mixed group failed: " + name,cause)
    override def endGroup(name: String, result: TestResult): Unit = require(result == TestResult.Passed,"Mixed group failed")
    override def doComplete(result: TestResult): Unit = require(result == TestResult.Passed,"Mixed command failed")
  }
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
    parser=argparse.ArgumentParser(); parser.add_argument('--evidence-dir',required=True,type=Path); parser.add_argument('--artifact-version',required=True); parser.add_argument('--sbt-version',nargs='+',required=True,choices=['2.0.9']); parser.add_argument('--scala-version',nargs='+',required=True,choices=['3.9.0','2.13.18']); a=parser.parse_args()
    out=a.evidence_dir.resolve(); out.mkdir(exist_ok=False); shutil.copy2(__file__,out/'driver.py')
    helper=ROOT/'test-fixtures/host-sharing-consumer/verify-held-forks.py'
    spec=importlib.util.spec_from_file_location('held_source',helper); module=importlib.util.module_from_spec(spec); spec.loader.exec_module(module)
    source=module.SOURCE.replace('HeldDeliveryFramework','CompletionAuditFramework').replace('distage-held-delivery-control','distage-mixed-completion-control')
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
                    value+='\nlazy val mixedConsumer = project.in(file(".")).enablePlugins(izumi.distage.sbt.DistageTestkitPlugin)\nTest / fork := true\n'+SETTINGS.replace('SELECTED','testSelected').replace('UNCACHED','Def.uncached')
                elif p.name in ['SuiteA.scala','SuiteB.scala']: value=value.replace('extends PlainFixtureSuite','extends DIFixtureSuite')
                dest.write_text(value)
            (build/'src/test/scala/izumi/fixtures/host/CompletionAuditFramework.scala').write_text(source)
            project=build/'project'; project.mkdir(); (project/'build.properties').write_text('sbt.version='+sdk+'\n'); (project/'plugins.sbt').write_text('addSbtPlugin("io.7mind.izumi" % "sbt-distage-testkit" % "'+a.artifact_version+'")\n')
            selected='testOnly '+' '.join(expected); commands=['prepareFixture baseline',selected,'captureMixedFixture baseline','prepareFixture repeat',selected,'captureMixedFixture repeat','show Test / dependencyClasspath','show Test / fullClasspath']
            if sdk=='2.0.9': commands.insert(0,'set Global / localCacheDirectory := file("'+str(lane/'local-cache')+'")')
            shell='task_sdk="$1"; shift; exec sbt --server --sbt-version "$task_sdk" -java-home "$JDK21" -batch -J-Xmx6G "$@"'
            argv=['direnv','exec',str(ROOT),'sh','-c',shell,'mixed-fork',sdk,'-Dizumi.fixture.scala-version='+scala,'-Dizumi.fixture.version='+a.artifact_version,'-Dizumi.fixture.audit-root='+str(build/'target/body-audit'),'-Dizumi.fixture.captures='+str(lane/'cases'),*commands]
            generated=[dict(path=str(p),sha256=sha(p)) for p in sorted(build.rglob('*')) if p.is_file()]; (lane/'commands.json').write_text(json.dumps(dict(cwd=str(build),argv=argv,inputs=generated),indent=2)+'\n')
            print('MIXED_FORK_LANE_START '+sdk+' '+scala,flush=True)
            with (lane/'run.log').open('x') as log:
                process=subprocess.Popen(argv,cwd=build,stdout=log,stderr=subprocess.STDOUT,start_new_session=True)
                try: actual=process.wait(timeout=TIMEOUT_SECONDS)
                except subprocess.TimeoutExpired:
                    os.killpg(process.pid,signal.SIGTERM)
                    try: process.wait(timeout=GRACE_SECONDS)
                    except subprocess.TimeoutExpired: os.killpg(process.pid,signal.SIGKILL); process.wait()
                    actual=124
                log.write('\nEXIT '+str(actual)+'\n')
            failures=[]; cases=[]; parents=set()
            if actual!=0: failures.append('Actual SBT command failed; preserved raw/target evidence requires failure diagnosis')
            else:
                for case in ['baseline','repeat']:
                    capture=lane/'cases'/case; audit=capture/'body-audit'; bodies=[p.read_text().split('\t') for p in audit.glob('*.body')]
                    acquired=[p.read_text() for p in audit.glob('*.acquire')]; released=[p.read_text() for p in audit.glob('*.release')]
                    if sorted((r[0],r[1]) for r in bodies)!=[(s,str(i)) for s in expected for i in range(1,4)]: failures.append('Mixed physical identities differ: '+case)
                    if len(acquired)!=1 or acquired!=released or set(acquired)&resources or {r[2] for r in bodies if r[0] in owned}!=set(acquired) or any(r[2]!='plain' for r in bodies if r[0] not in owned): failures.append('Mixed DI/foreign lifetime differs: '+case)
                    resources.update(acquired)
                    output=[line.split('\t') for line in (audit/'host.result-counts').read_text().splitlines()]
                    if output!=[[s,'Passed','3','0','0','0'] for s in expected]: failures.append('Mixed SDK output identities/counts differ: '+case)
                    parent=(audit/'host.parent-pid').read_text(); parents.add(parent)
                    done=(audit/'target.done-enter').read_text(); child=done.split('\t')[0].removeprefix('pid=')
                    if child==parent or done!=(audit/'target.done-finish').read_text() or done.split('\t')[1]!='suites='+','.join(owned): failures.append('Mixed counted completion identity differs: '+case)
                    directory=Path((audit/'host-command.fork-dir').read_text())
                    if directory.exists() or str(directory) in directories or not directory.is_relative_to(build/'target'): failures.append('Mixed command directory ownership/cleanup differs: '+case)
                    directories.add(str(directory)); reported=[]; reports=list((capture/'test-reports').glob('*.xml'))
                    for p in reports:
                        xml=ElementTree.parse(p); summary=xml.getroot().attrib
                        if summary['tests']!='3' or any(summary[k]!='0' for k in ['failures','errors','skipped']) or any(xml.findall('.//'+k) for k in ['failure','error','skipped']): failures.append('Mixed XML result differs: '+case)
                        reported.extend((n.attrib['classname'],n.attrib['name']) for n in xml.findall('.//testcase'))
                    if len(reports)!=6 or sorted(reported)!=sorted((s,'equal display name should '+leaf) for s in expected for leaf in ['first','second','third']): failures.append('Mixed XML IDs differ: '+case)
                    cases.append(dict(case=case,bodies=len(bodies),xmlCases=len(reported),outputSuites=len(output),resource=acquired[0],receiptDirectory=str(directory),parentPid=parent,childPid=child))
                if len(parents)!=1: failures.append('Mixed repeat changed parent SBT process')
            for row in sources+generated: assert sha(row['path'])==row['sha256']
            assert sha(helper)==sha(out/'source-helper.py') and sha(__file__)==sha(out/'driver.py')
            row=dict(sbt=sdk,scala=scala,actualExit=actual,validationFailures=failures,cases=cases); (lane/'completion.json').write_text(json.dumps(row,indent=2)+'\n'); outcomes.append(row); print(json.dumps(row),flush=True)
            if failures: break
        if outcomes[-1]['validationFailures']: break
    valid=len(outcomes)==len(a.sbt_version)*len(a.scala_version) and all(not r['validationFailures'] for r in outcomes)
    terminal=dict(exit=0 if valid else 1,outcomes=outcomes,scope='Published counted production Runner/Tasks forwarded for completion auditing; original foreign framework/source untouched. Five DI suites plus foreign three-body suite, exact physical/public SDK output/XML sets and same-session repeat. No held foreign delivery/logger/error/history/cancellation/final acceptance.')
    (out/'completion.json').write_text(json.dumps(terminal,indent=2)+'\n'); print(json.dumps(dict(exit=terminal['exit'],completionSha256=sha(out/'completion.json'))),flush=True); raise SystemExit(terminal['exit'])
if __name__=='__main__': main()
