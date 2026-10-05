from pathlib import Path
from xml.etree import ElementTree
import argparse
import hashlib
import json
import os
import shutil
import signal
import subprocess

TIMEOUT_SECONDS = 600
SUITES = ['SuiteA','SuiteB','SuiteC','SuiteD','SuiteE']
PREFIX = 'izumi.fixtures.host.'

BUILD = r'''
import izumi.distage.sbt.DistageTestkitPlugin.autoImport.*
import sbt.complete.DefaultParsers.spaceDelimited
import sbt.plugins.JUnitXmlReportPlugin.autoImport.*
ThisBuild / scalaVersion := sys.props("fixture.scala-version")
ThisBuild / publish / skip := true
lazy val Integration = config("it").extend(Test)
val fixtureAudit = settingKey[File]("Owned audit directory for this project and configuration")
val prepareBatch = inputKey[Unit]("Prepare every owned module/configuration audit")
val captureBatch = inputKey[Unit]("Freeze every owned module/configuration result")

def contextSettings: Seq[Def.Setting[?]] = Seq(
  fixtureAudit := baseDirectory.value / "target" / ("audit-" + configuration.value.name),
  testReportsDirectory := target.value / "test-reports",
  fork := true,
  testFrameworks := Seq(new TestFramework("izumi.distage.testkit.runner.bootstrap.Framework"),new TestFramework("izumi.fixtures.host.ForeignFramework")),
  javaOptions ++= Def.uncached { Seq("-Dizumi.fixture.audit-root=" + fixtureAudit.value.getAbsolutePath,"-Dfixture.module-target=" + thisProject.value.id + "/" + configuration.value.name) },
  testSelected / testResultLogger := {
    val inherited = (testSelected / testResultLogger).value
    val audit = fixtureAudit.value
    new TestResultLogger {
      override def run(log: sbt.util.Logger, output: Tests.Output, taskName: String): Unit = {
        IO.write(audit / "host.parent",ProcessHandle.current().pid().toString)
        val rows = output.events.toVector.sortBy(_._1).map { case (name,result) => Vector(name,result.passedCount,result.failureCount,result.errorCount).mkString("\t") }
        IO.write(audit / "host.output",rows.mkString("\n"))
        inherited.run(log,output,taskName)
      }
    }
  },
  testQuick / testResultLogger := {
    val inherited = (testQuick / testResultLogger).value
    val audit = fixtureAudit.value
    new TestResultLogger {
      override def run(log: sbt.util.Logger, output: Tests.Output, taskName: String): Unit = {
        IO.write(audit / "host.parent",ProcessHandle.current().pid().toString)
        val rows = output.events.toVector.sortBy(_._1).map { case (name,result) => Vector(name,result.passedCount,result.failureCount,result.errorCount).mkString("\t") }
        IO.write(audit / "host.output",rows.mkString("\n"))
        inherited.run(log,output,taskName)
      }
    }
  },
)
def moduleSettings: Seq[Def.Setting[?]] = Seq(
  libraryDependencies ++= Seq("io.7mind.izumi" %% "distage-testkit-runner" % sys.props("fixture.artifact-version") % Test,"org.typelevel" %% "cats-effect" % "3.7.1","dev.zio" %% "zio" % "2.1.26" excludeAll("dev.zio" %% "izumi-reflect")),
  libraryDependencies ++= { if (scalaVersion.value.startsWith("3.")) Seq.empty else Seq(compilerPlugin("org.typelevel" % "kind-projector" % "0.13.4" cross CrossVersion.full)) },
  scalacOptions ++= { if (scalaVersion.value.startsWith("3.")) Seq("-release:17","-Ybackend-parallelism","1","-Yretain-trees","-Xmax-inlines:64","-Wunused:all","-Xkind-projector:underscores") else Seq("-release:17","-Xsource:3","-P:kind-projector:underscore-placeholders") },
  Test / target := baseDirectory.value / "target/test",
  Integration / target := baseDirectory.value / "target/it",
  Integration / unmanagedSourceDirectories := (Test / unmanagedSourceDirectories).value,
)
lazy val moduleA = project.in(file("moduleA")).enablePlugins(izumi.distage.sbt.DistageTestkitPlugin).configs(Integration)
  .settings(inConfig(Test)(contextSettings))
  .settings(inConfig(Integration)(Defaults.testSettings ++ Seq(testListeners := Nil) ++ testReportSettings ++ distageTestSettings ++ contextSettings))
  .settings(moduleSettings)
lazy val moduleB = project.in(file("moduleB")).enablePlugins(izumi.distage.sbt.DistageTestkitPlugin).configs(Integration)
  .settings(inConfig(Test)(contextSettings))
  .settings(inConfig(Integration)(Defaults.testSettings ++ Seq(testListeners := Nil) ++ testReportSettings ++ distageTestSettings ++ contextSettings))
  .settings(moduleSettings)
lazy val root = project.in(file(".")).aggregate(moduleA,moduleB).configs(Integration)
  .settings(inConfig(Integration)(Defaults.testSettings))
prepareBatch / aggregate := false
captureBatch / aggregate := false

prepareBatch := {
  val parsed = spaceDelimited("case").parsed
  require(parsed.size == 1,"Missing batch case")
  val contexts = Vector(
    ((moduleA / Test / fixtureAudit).value,(moduleA / Test / target).value),
    ((moduleB / Test / fixtureAudit).value,(moduleB / Test / target).value),
    ((moduleA / Integration / fixtureAudit).value,(moduleA / Integration / target).value),
    ((moduleB / Integration / fixtureAudit).value,(moduleB / Integration / target).value),
  )
  contexts.foreach { case (audit,target) => IO.delete(audit); IO.createDirectory(audit); IO.delete(target / "test-reports") }
  streams.value.log.info("MULTI_PROJECT_PREPARED case=" + parsed.head)
}
captureBatch := {
  val parsed = spaceDelimited("case").parsed
  require(parsed.size == 1,"Missing capture case")
  val destination = file(sys.props("fixture.captures")) / parsed.head
  require(!destination.exists(),"Capture must be new")
  val contexts = Vector(
    ("moduleA-test",(moduleA / Test / fixtureAudit).value,(moduleA / Test / target).value),
    ("moduleB-test",(moduleB / Test / fixtureAudit).value,(moduleB / Test / target).value),
    ("moduleA-it",(moduleA / Integration / fixtureAudit).value,(moduleA / Integration / target).value),
    ("moduleB-it",(moduleB / Integration / fixtureAudit).value,(moduleB / Integration / target).value),
  )
  contexts.foreach { case (name,audit,target) =>
    IO.copyDirectory(audit,destination / name / "audit")
    IO.copyDirectory(target / "test-reports",destination / name / "test-reports")
    val roots = target / "distage-fork-receipts"
    require(!roots.exists() || (roots * "*").get().isEmpty,"Command root survived: " + name)
  }
  streams.value.log.info("MULTI_PROJECT_CAPTURE_OK case=" + parsed.head)
}
'''

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--artifact-version',required=True)
    parser.add_argument('--scala-version',nargs='+',choices=['3.9.0','2.13.18'],required=True)
    parser.add_argument('--evidence-dir',type=Path,required=True)
    args = parser.parse_args()
    repo = Path(__file__).resolve().parents[2]
    out = args.evidence_dir.resolve(); out.mkdir()
    shutil.copy2(__file__,out/'driver.py')
    sources = sorted((repo/'test-fixtures/host-sharing-consumer/src/test/scala').rglob('*.scala'))
    outcomes = []
    for scala in args.scala_version:
        lane = out/('scala'+scala); build = lane/'build'; (build/'project').mkdir(parents=True)
        (build/'project/build.properties').write_text('sbt.version=2.0.9\n')
        (build/'project/plugins.sbt').write_text('addSbtPlugin("io.7mind.izumi" % "sbt-distage-testkit" % "'+args.artifact_version+'")\n')
        (build/'build.sbt').write_text(BUILD)
        for module in ['moduleA','moduleB']:
            for source in sources:
                target = build/module/source.relative_to(repo/'test-fixtures/host-sharing-consumer')
                target.parent.mkdir(parents=True,exist_ok=True)
                text = source.read_text()
                if source.name=='FixtureSuites.scala':
                    before = 'resource.getOrElse("plain")'
                    assert text.count(before)==1
                    text = text.replace(before,before+' + "\\t" + ProcessHandle.current().pid() + "\\t" + sys.props("fixture.module-target")')
                target.write_text(text)
        contexts = ['moduleA-test','moduleB-test','moduleA-it','moduleB-it']
        cases = [
            ('full','testFull',{name:SUITES+['ForeignSuite'] for name in contexts[:2]}),
            ('selected','testOnly *SuiteC *SuiteD',{name:['SuiteC','SuiteD'] for name in contexts[:2]}),
            ('quick','testQuick',{name:SUITES for name in contexts[:2]}),
            ('incremental','test',{name:SUITES for name in contexts[:2]}),
            ('integration-selected','Integration / testOnly *SuiteC *SuiteD',{name:['SuiteC','SuiteD'] for name in contexts[2:]}),
            ('integration-quick','Integration / testQuick *SuiteD',{name:['SuiteD'] for name in contexts[2:]}),
            ('one-module','moduleA / testOnly *SuiteA *SuiteB',{'moduleA-test':['SuiteA','SuiteB']}),
            ('foreign-explicit','moduleA / testOnly *ForeignSuite',{'moduleA-test':['ForeignSuite']}),
            ('after-one-module','testQuick',{name:SUITES for name in contexts[:2]}),
            ('inspection','moduleA / Test / distageList',{}),
        ]
        commands = ['set Global / localCacheDirectory := file("'+str(lane/'local-cache')+'")']
        for name,request,expected in cases:
            commands += ['prepareBatch '+name,request]
            if name=='inspection': commands += ['moduleB / Test / distageList','moduleA / Integration / distageList','moduleB / Integration / distageList']
            commands += ['captureBatch '+name]
        inputs = [dict(path=str(path),sha256=hashlib.sha256(path.read_bytes()).hexdigest()) for path in sorted(build.rglob('*')) if path.is_file()]
        argv = ['direnv','exec',str(repo),'sh','-c','exec sbt --server --sbt-version 2.0.9 -java-home "$JDK21" -batch -J-Xmx6G "$@"','multi-project-batch','-Dfixture.scala-version='+scala,'-Dfixture.artifact-version='+args.artifact_version,'-Dfixture.captures='+str(lane/'cases'),*commands]
        (lane/'commands.json').write_text(json.dumps(dict(cwd=str(build),argv=argv,inputs=inputs,cases=cases),indent=2)+'\n')
        print('MULTI_PROJECT_BATCH_START scala='+scala,flush=True)
        with (lane/'run.log').open('x') as log:
            process = subprocess.Popen(argv,cwd=build,stdout=log,stderr=subprocess.STDOUT,start_new_session=True)
            try: code = process.wait(timeout=TIMEOUT_SECONDS)
            except subprocess.TimeoutExpired:
                os.killpg(process.pid,signal.SIGTERM)
                try: process.wait(timeout=10)
                except subprocess.TimeoutExpired: os.killpg(process.pid,signal.SIGKILL); process.wait()
                code = 124
        failures = []; resources = set(); pids = set(); parents = set(); checks = []
        if code: failures.append('SBT process failed: inspect run.log')
        else:
            for name,request,expected in cases:
                for context in contexts:
                    case = lane/'cases'/name/context
                    labels = expected.get(context,[])
                    rows = [path.read_text().split('\t') for path in (case/'audit').glob('*.body')]
                    wanted = sorted((PREFIX+label,str(index)) for label in labels for index in range(1,4))
                    if sorted((row[0],row[1]) for row in rows)!=wanted: failures.append('Body identities differ: '+name+'/'+context)
                    expected_target = context.replace('-','/')
                    if any(len(row)!=5 or row[4]!=expected_target for row in rows): failures.append('Module/configuration ownership differs: '+name+'/'+context)
                    child_pids = {row[3] for row in rows}
                    if len(child_pids)!=(1 if labels else 0) or child_pids & pids: failures.append('Worker reuse/membership differs: '+name+'/'+context)
                    pids |= child_pids
                    for pid in child_pids:
                        try: os.kill(int(pid),0); failures.append('Worker survived command: '+pid)
                        except ProcessLookupError: pass
                    acquired = [path.read_text() for path in (case/'audit').glob('*.acquire')]
                    released = [path.read_text() for path in (case/'audit').glob('*.release')]
                    wanted_resources = 1 if set(labels)&{'SuiteC','SuiteD','SuiteE'} else 0
                    if len(acquired)!=wanted_resources or sorted(acquired)!=sorted(released) or set(acquired)&resources: failures.append('Memoized lifetime differs: '+name+'/'+context)
                    resources.update(acquired)
                    xml = [ElementTree.parse(path) for path in (case/'test-reports').glob('*.xml')]
                    reported = sorted((node.attrib['classname'],node.attrib['name']) for tree in xml for node in tree.findall('.//testcase'))
                    wanted_xml = sorted((PREFIX+label,'equal display name should '+leaf) for label in labels for leaf in ['first','second','third'])
                    if reported!=wanted_xml or any(tree.findall('.//error') or tree.findall('.//failure') or tree.findall('.//skipped') for tree in xml): failures.append('Report identities/status differ: '+name+'/'+context)
                    output = case/'audit/host.output'
                    if labels and name!='full':
                        wanted_output = '\n'.join(PREFIX+label+'\t3\t0\t0' for label in sorted(labels))
                        if not output.is_file() or output.read_text()!=wanted_output: failures.append('Public Output differs: '+name+'/'+context)
                        parents.add((case/'audit/host.parent').read_text())
                    checks.append(dict(case=name,context=context,bodies=len(rows),xmlCases=len(reported),resources=acquired,pids=sorted(child_pids)))
            if len(parents)!=1 or parents & pids: failures.append('Host session/process isolation differs')
            frames = []
            for line in (lane/'run.log').read_text().splitlines():
                if '{"schemaVersion"' not in line: continue
                frame = json.loads(line[line.index('{"schemaVersion"'):])['message']
                if frame['kind']=='resolved': frames.append(frame['selection'])
            targets = {selection['request']['identity']['target'] for selection in frames}
            if len(frames)!=len(contexts) or targets!={context.replace('-','/') for context in contexts}: failures.append('Inspection target identities differ')
            if any(len(selection['tests'])!=15 or any(test['id']['target']!=selection['request']['identity']['target'] for test in selection['tests']) for selection in frames): failures.append('Inspection test identity ownership differs')
        for row in inputs: assert hashlib.sha256(Path(row['path']).read_bytes()).hexdigest()==row['sha256']
        result = dict(scala=scala,actualExit=code,failures=failures,checks=checks,scope='Two aggregated projects, Test and Integration configurations, stock full/selected/incremental/quick commands, independent owned and foreign frameworks, process/resource isolation and exact body/Output/XML reconciliation. Full Output is checked by production; input/quick Output is independently captured.')
        (lane/'completion.json').write_text(json.dumps(result,indent=2)+'\n'); outcomes.append(result)
        print(json.dumps(dict(scala=scala,actualExit=code,failures=failures,checks=len(checks))),flush=True)
        if code or failures: break
    result = dict(exit=0 if len(outcomes)==len(args.scala_version) and all(not row['actualExit'] and not row['failures'] for row in outcomes) else 1,outcomes=outcomes)
    (out/'completion.json').write_text(json.dumps(result,indent=2)+'\n')
    raise SystemExit(result['exit'])

if __name__=='__main__': main()
