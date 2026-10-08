#!/usr/bin/env python3
from pathlib import Path
import argparse
import json
import re
from xml.etree import ElementTree
import sys
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from fixture_harness import freeze_driver, run_process, sha, freeze_sources

ROOT=Path(__file__).resolve().parents[2]
LANE_TIMEOUT_SECONDS=600
SHUTDOWN_GRACE_SECONDS=10
SUITES=['Suite'+letter for letter in 'ABCDE']
TESTS_PER_SUITE=3
EXPECTED_BODIES=len(SUITES)*TESTS_PER_SUITE
CONTROL='''
val changeDeathMode = inputKey[Unit]("Change only the owned target-death input without reapplying SBT settings")
changeDeathMode := {
  val parsed = spaceDelimited("mode").parsed
  require(parsed.size == 1 && Set("normal", "after-release").contains(parsed.head), "Invalid death mode")
  val path = file(sys.props("izumi.fixture.death-mode-file"))
  require(path.getCanonicalFile == (baseDirectory.value / "target/death-mode.txt").getCanonicalFile, "Death input is outside the owned fixture")
  IO.write(path, parsed.head)
  streams.value.log.info("TARGET_DEATH_MODE pid=" + ProcessHandle.current().pid() + " mode=" + parsed.head)
}
val verifyDeathParent = taskKey[Unit]("Verify the target-death control is absent from the SBT process")
verifyDeathParent := UNCACHED {
  require(!sys.props.contains("izumi.fixture.target-death"), "Target halt control leaked into SBT")
  streams.value.log.info("TARGET_DEATH_PARENT pid=" + ProcessHandle.current().pid())
}
val verifyTargetDeath = taskKey[Unit]("Require a forked target death to fail before same-session recovery")
verifyTargetDeath := UNCACHED {
  val result = (Test / FULL).result.value
  require(result.toEither.isLeft, "TARGET_DEATH_FALSE_SUCCESS")
  val audit = file(sys.props("izumi.fixture.audit-root"))
  val halt = (audit * "*.halt").get()
  require(halt.size == 1, "Target did not reach its controlled halt")
  val fields = IO.read(halt.head).split("\\t", -1)
  require(fields.size == 3 && fields(1).toLong != ProcessHandle.current().pid() && fields(2) == "15", "Target halt identity/body count differs")
  require((audit * "*.body").get().size == 15, "Target death preceded the selected bodies")
  val acquired = (audit * "*.acquire").get().map(IO.read(_))
  val released = (audit * "*.release").get().map(IO.read(_))
  require(acquired.size == 1 && acquired == released && fields.head == acquired.head, "Target death lifetime differs")
  val capture = file(sys.props("izumi.fixture.captures")) / "death"
  require(!capture.exists(), "Death capture must be new")
  IO.copyDirectory(audit, capture / "body-audit")
  IO.copyDirectory(target.value / "test-reports", capture / "test-reports")
  val error = result.toEither.left.toOption.get
  require(error.toString.contains("Incomplete distage host result") && "ABCDE".forall(letter => error.toString.contains("izumi.fixtures.host.Suite" + letter)), "Target died for an unexpected task failure")
  IO.write(capture / "task-failure.txt", error.toString + "\\n" + error.directCause.map(_.toString).getOrElse("no direct cause"))
  streams.value.log.info("TARGET_DEATH_REJECTED child=" + fields(1) + " bodies=15 failure=" + error)
}
'''
HALT='''    if (new String(Files.readAllBytes(Paths.get(sys.props("izumi.fixture.death-mode-file"))), StandardCharsets.UTF_8) == "after-release") {
      val files = Files.list(resource.directory)
      val bodyCount = try files.filter(path => path.getFileName.toString.endsWith(".body")).count() finally files.close()
      val data = resource.id + "\\t" + ProcessHandle.current().pid() + "\\t" + bodyCount
      val _ = Files.write(resource.directory.resolve(resource.id + ".halt"), data.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
      println("TARGET_DEATH_HALT pid=" + ProcessHandle.current().pid() + " bodies=" + bodyCount + " exit=0")
      Runtime.getRuntime.halt(0)
    }
'''

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--artifact-version',required=True)
    parser.add_argument('--sbt-version',nargs='+',required=True,choices=['2.0.9'])
    parser.add_argument('--scala-version',nargs='+',required=True,choices=['3.9.0','2.13.18'])
    parser.add_argument('--evidence-dir',required=True,type=Path)
    arguments=parser.parse_args()
    evidence=arguments.evidence_dir.resolve()
    evidence.mkdir(parents=True,exist_ok=False)
    driver=evidence/'driver.py'
    freeze_driver(__file__,driver)
    (evidence/'driver.json').write_text(json.dumps(dict(original=str(Path(__file__).resolve()),frozen=str(driver),sha256=sha(driver)),indent=2)+'\n')
    fixture=ROOT/'test-fixtures/host-sharing-consumer'
    sources=[fixture/'build.sbt',*sorted((fixture/'src').rglob('*.scala'))]
    rows = freeze_sources(sources, fixture, evidence / 'sources')
    (evidence/'inputs.json').write_text(json.dumps(dict(sources=rows),indent=2)+'\n')
    outcomes=[]
    resource_ids=set()
    host_directories=set()
    for sdk in arguments.sbt_version:
        for scala in arguments.scala_version:
            lane=evidence/('sbt'+sdk+'-scala'+scala)
            build=lane/'build'
            build.mkdir(parents=True)
            full='testFull'
            quick='test'
            for row in rows:
                original=Path(row['path'])
                dest=build/original.relative_to(fixture)
                dest.parent.mkdir(parents=True,exist_ok=True)
                text=Path(row['frozen']).read_text()
                if original.name=='build.sbt':
                    start=text.index('Test / testFrameworks :=')
                    end=text.index('Test / javaOptions +=',start)
                    text=text[:start]+text[end:]
                    before='val diLabels = Set("SuiteC", "SuiteD", "SuiteE")'
                    assert text.count(before)==1
                    text=text.replace(before,'val diLabels = Set("SuiteA", "SuiteB", "SuiteC", "SuiteD", "SuiteE")')
                    text+='\nlazy val deathConsumer = project.in(file(".")).enablePlugins(izumi.distage.sbt.DistageTestkitPlugin)\nTest / fork := true\nTest / javaOptions += "-Dizumi.fixture.death-mode-file=" + sys.props("izumi.fixture.death-mode-file")\n'
                    text+=CONTROL.replace('UNCACHED','Def.uncached').replace('FULL',full)
                elif original.name in ['SuiteA.scala','SuiteB.scala']:
                    assert text.count('extends PlainFixtureSuite')==1
                    text=text.replace('extends PlainFixtureSuite','extends DIFixtureSuite')
                elif original.name=='FixturePlugin.scala':
                    before='    ()\n  }'
                    assert text.count(before)==1
                    text=text.replace(before,HALT+before)
                    before='    resource\n  } { resource =>'
                    assert text.count(before)==1
                    acquisition='    val processData = resource.id + "\t" + ProcessHandle.current().pid()\n    val processReceipt = Files.write(resource.directory.resolve(resource.id + ".process"), processData.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)\n    require(Files.isRegularFile(processReceipt), "Target process receipt was not written")\n'
                    text=text.replace(before,acquisition+before)
                dest.write_text(text)
            with (build/"build.sbt").open("a") as extra: extra.write(FORK_SETTINGS.replace("UNCACHED", "Def.uncached"))
            project=build/'project'
            project.mkdir()
            (project/'build.properties').write_text('sbt.version='+sdk+'\n')
            (project/'plugins.sbt').write_text('addSbtPlugin("io.7mind.izumi" % "sbt-distage-testkit" % "'+arguments.artifact_version+'")\n')
            sequence=['show Test / target','changeDeathMode normal','verifyDeathParent','show Test / fork','prepareFixture baseline',full,'verifyFixture baseline 1 '+' '.join(SUITES),'verifyForkReceiptCleanup','changeDeathMode after-release','prepareFixture death','verifyTargetDeath','verifyForkReceiptCleanup','changeDeathMode normal','verifyDeathParent','prepareFixture recovery',quick,'verifyFixture recovery 1 '+' '.join(SUITES),'verifyForkReceiptCleanup','prepareFixture repeat',quick,'verifyFixture repeat 1 '+' '.join(SUITES),'verifyForkReceiptCleanup','show Test / dependencyClasspath','show Test / fullClasspath']
            if sdk=='2.0.9': sequence.insert(0,'set Global / localCacheDirectory := file("'+str(lane/'local-cache')+'")')
            shell='task_sdk="$1"; shift; exec sbt --server --sbt-version "$task_sdk" -java-home "$JDK21" -batch -J-Xmx6G "$@"'
            argv=['direnv','exec',str(ROOT),'sh','-c',shell,'fork-target-death',sdk,'-Dizumi.fixture.scala-version='+scala,'-Dizumi.fixture.version='+arguments.artifact_version,'-Dizumi.fixture.audit-root='+str(build/'target/body-audit'),'-Dizumi.fixture.captures='+str(lane/'cases'),'-Dizumi.fixture.death-mode-file='+str(build/'target/death-mode.txt'),*sequence]
            generated=[dict(path=str(p),sha256=sha(p)) for p in sorted(build.rglob('*')) if p.is_file()]
            (lane/'commands.json').write_text(json.dumps(dict(cwd=str(build),argv=argv,inputs=generated),indent=2)+'\n')
            print('TARGET_DEATH_LANE_START '+sdk,flush=True)
            with (lane/'run.log').open('x') as log:
                code = run_process(argv, build, log, LANE_TIMEOUT_SECONDS, SHUTDOWN_GRACE_SECONDS)
                log.write('\nEXIT '+str(code)+'\n')
            failures=[]
            if code:
                failures.append('Actual SBT process failed before the full control sequence')
                outcome=dict(sbt=sdk,scala=scala,actualExit=code,validationFailures=failures)
                (lane/'completion.json').write_text(json.dumps(outcome,indent=2)+'\n')
                outcomes.append(outcome)
                (evidence/'completion.json').write_text(json.dumps(dict(exit=1,outcomes=outcomes),indent=2)+'\n')
                print(json.dumps(outcome),flush=True)
                raise SystemExit(1)
            raw=(lane/'run.log').read_text()
            if raw.count('FORK_RECEIPT_CLEANUP_OK parent=') != 4: failures.append('Four command cleanups did not complete')
            if raw.count('TARGET_DEATH_REJECTED child=')!=1 or raw.count('TARGET_DEATH_HALT pid=')!=1: failures.append('Death control markers differ')
            parents=re.findall(r'^\[info\] TARGET_DEATH_PARENT pid=(\d+)$',raw,re.M)
            modes=re.findall(r'^\[info\] TARGET_DEATH_MODE pid=(\d+) mode=(normal|after-release)$',raw,re.M)
            if len(parents)!=2 or len(set(parents))!=1 or [mode for _,mode in modes]!=['normal','after-release','normal'] or {pid for pid,_ in modes}!=set(parents): failures.append('Same SBT process controls differ')
            death_segment=raw.split('TARGET_DEATH_MODE pid=',2)[-1]
            if 'Reapplying settings' in death_segment: failures.append('Settings reapplied between target death and recovery')
            receipt_parents=re.findall(r'^\[info\] FORK_RECEIPT_CLEANUP_OK parent=(.+)$',raw,re.M)
            if len(receipt_parents)!=4 or len(set(receipt_parents))!=1: failures.append('Receipt parent observations differ')
            for case in ['baseline','death','recovery','repeat']:
                capture=lane/'cases'/case
                audit=capture/'body-audit'
                owned=Path((audit/'host-command.fork-dir').read_text())
                if str(owned.parent) not in receipt_parents or not owned.is_relative_to(build/'target') or owned.exists() or str(owned) in host_directories: failures.append('Fresh command directory ownership/cleanup differs: '+case)
                host_directories.add(str(owned))
                bodies=[p.read_text().split('\t') for p in audit.glob('*.body')]
                acquired=[p.read_text().strip() for p in audit.glob('*.acquire')]
                released=[p.read_text().strip() for p in audit.glob('*.release')]
                expected=[('izumi.fixtures.host.'+suite,str(i)) for suite in SUITES for i in range(1,TESTS_PER_SUITE+1)]
                if sorted((r[0],r[1]) for r in bodies)!=sorted(expected): failures.append('Physical IDs differ: '+case)
                if len(acquired)!=1 or acquired!=released or {r[2] for r in bodies}!=set(acquired) or resource_ids.intersection(acquired): failures.append('Resource lifetime differs: '+case)
                resource_ids.update(acquired)
                process_rows=[p.read_text().split('\t') for p in audit.glob('*.process')]
                if len(process_rows)!=1 or len(process_rows[0])!=2 or process_rows[0][0] not in acquired or process_rows[0][1] in parents: failures.append('Physical target process identity differs: '+case)
                if case=='death':
                    halts=[p.read_text().split('\t') for p in audit.glob('*.halt')]
                    if len(halts)!=1 or len(halts[0])!=3 or halts[0][:2] not in process_rows or halts[0][2]!=str(EXPECTED_BODIES): failures.append('Target halt receipt differs')
                    failure=(capture/'task-failure.txt').read_text()
                    if 'Incomplete distage host result' not in failure or not all('izumi.fixtures.host.'+suite in failure for suite in SUITES): failures.append('Target death task failure differs')
                if case!='death':
                    identities=[]
                    for p in (capture/'test-reports').glob('*.xml'):
                        xml=ElementTree.parse(p)
                        s=xml.getroot()
                        if s.attrib['tests']!=str(TESTS_PER_SUITE) or any(s.attrib[k]!='0' for k in ['errors','failures','skipped']) or any(xml.findall('.//'+k) for k in ['error','failure','skipped']): failures.append('XML outcome differs: '+case)
                        identities.extend((n.attrib['classname'],n.attrib['name']) for n in xml.findall('.//testcase'))
                    expected_xml=[('izumi.fixtures.host.'+s,'equal display name should '+leaf) for s in SUITES for leaf in ['first','second','third']]
                    if sorted(identities)!=sorted(expected_xml): failures.append('XML IDs differ: '+case)
            for r in rows: assert sha(r['path'])==sha(r['frozen'])==r['sha256']
            for r in generated: assert sha(r['path'])==r['sha256']
            outcome=dict(sbt=sdk,scala=scala,actualExit=code,validationFailures=failures)
            (lane/'completion.json').write_text(json.dumps(outcome,indent=2)+'\n')
            outcomes.append(outcome)
            print(json.dumps(outcome),flush=True)
            if failures:
                (evidence/'completion.json').write_text(json.dumps(dict(exit=1,outcomes=outcomes),indent=2)+'\n')
                raise SystemExit(1)
    (evidence/'completion.json').write_text(json.dumps(dict(exit=0,outcomes=outcomes,resourceLifetimes=len(resource_ids),hostCommandDirectories=len(host_directories),scope='Production built-in counted fork receipts; isolated five-DI-suite fork death after fifteen physical bodies and resource release; exit-zero halt and same-SBT-session incremental recovery without settings reapplication; owned positive XML verified; held delivery, actual task error, mixed framework, history and cancellation acceptance remain open.'),indent=2)+'\n')


FORK_SETTINGS='\nval verifyForkReceiptCleanup = taskKey[Unit]("Check cleanup of fresh command receipt directories")\nverifyForkReceiptCleanup := UNCACHED {\n  val parent = (Test / target).value / "distage-fork-receipts"\n  require(parent.exists() && (parent * "*").get().isEmpty, "Owned receipt directory survived command completion")\n  streams.value.log.info("FORK_RECEIPT_CLEANUP_OK parent=" + parent.getCanonicalPath)\n}\nTest / testListeners += {\n  val fixtureReceiptParent = target.value / "distage-fork-receipts"\n  new TestsListener {\n  override def doInit(): Unit = {\n    val parent = fixtureReceiptParent\n    val directories = (parent * "command-*").get().filter(_.isDirectory)\n    require(directories.size == 1, "Expected one fresh command receipt directory")\n    IO.write(file(sys.props("izumi.fixture.audit-root")) / "host-command.fork-dir", directories.head.getCanonicalPath)\n  }\n  override def startGroup(name: String): Unit = ()\n  override def testEvent(event: TestEvent): Unit = ()\n  override def endGroup(name: String, cause: Throwable): Unit = ()\n  override def endGroup(name: String, result: TestResult): Unit = ()\n  override def doComplete(result: TestResult): Unit = ()\n}\n}\n'
if __name__ == '__main__':
    main()
