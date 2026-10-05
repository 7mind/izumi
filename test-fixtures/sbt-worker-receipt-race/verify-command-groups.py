from pathlib import Path
from xml.etree import ElementTree
import argparse
import hashlib
import json
import os
import shutil
import signal
import subprocess
import time

TIMEOUT_SECONDS = 240
SOURCE = r'''package fixture
import sbt.testing.{Event,EventHandler,Fingerprint,Framework,Logger,OptionalThrowable,Runner,Selector,Status,SubclassFingerprint,Task,TaskDef,TestSelector}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files,Paths,StandardOpenOption}
abstract class GroupSpec
final class SuiteA extends GroupSpec
final class SuiteB extends GroupSpec
final class GroupFramework extends Framework {
  private val fp = new SubclassFingerprint {
    override def isModule(): Boolean = false
    override def superclassName(): String = classOf[GroupSpec].getName
    override def requireNoArgConstructor(): Boolean = true
  }
  override def name(): String = "fork-command-groups"
  override def fingerprints(): Array[Fingerprint] = Array(fp)
  override def runner(arguments: Array[String], remoteArguments: Array[String], loader: ClassLoader): Runner = new Runner {
    override def args(): Array[String] = arguments.clone()
    override def remoteArgs(): Array[String] = remoteArguments.clone()
    override def done(): String = ""
    override def tasks(definitions: Array[TaskDef]): Array[Task] = definitions.sortBy(_.fullyQualifiedName()).map { definition => new Task {
      override def taskDef(): TaskDef = definition
      override def tags(): Array[String] = Array.empty
      override def execute(handler: EventHandler, loggers: Array[Logger]): Array[Task] = {
        val audit = Paths.get(sys.props("fixture.audit-root"))
        val pid = ProcessHandle.current().pid().toString
        (1 to 3).foreach { index =>
          val text = definition.fullyQualifiedName() + "\t" + index + "\t" + pid
          val written = Files.write(audit.resolve(definition.fullyQualifiedName() + "-" + index + "-" + pid + ".body"),text.getBytes(StandardCharsets.UTF_8),StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)
          require(Files.isRegularFile(written),"Body receipt missing")
          handler.handle(new Event {
            override def fullyQualifiedName(): String = definition.fullyQualifiedName()
            override def fingerprint(): Fingerprint = definition.fingerprint()
            override def selector(): Selector = new TestSelector("body-" + index)
            override def status(): Status = Status.Success
            override def throwable(): OptionalThrowable = new OptionalThrowable
            override def duration(): Long = 0L
          })
        }
        val mode = new String(Files.readAllBytes(audit.resolve("mode")),StandardCharsets.UTF_8)
        if (mode == "halt" && definition.fullyQualifiedName() == "fixture.SuiteB") {
          Runtime.getRuntime.addShutdownHook(new Thread(() => {
            val written = Files.write(audit.resolve("worker.held"),pid.getBytes(StandardCharsets.UTF_8),StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)
            require(Files.isRegularFile(written),"Shutdown hold marker missing")
            val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(20L)
            while (!Files.isRegularFile(audit.resolve("worker.allow")) && System.nanoTime() < deadline) Thread.sleep(5L)
            require(Files.isRegularFile(audit.resolve("worker.allow")),"Shutdown hook was not released")
          }))
        }
        if (mode == "halt" && definition.fullyQualifiedName() == "fixture.SuiteA") Runtime.getRuntime.halt(0)
        Array.empty
      }
    }}
  }
}
'''
BUILD = r'''
lazy val groupsConsumer = project.in(file(".")).enablePlugins(izumi.distage.sbt.DistageTestkitPlugin)
scalaVersion := sys.props("fixture.scala-version")
libraryDependencies += "io.7mind.izumi" %% "distage-test-runner" % sys.props("fixture.artifact-version") % Test
scalacOptions ++= { if (scalaVersion.value.startsWith("3.")) Seq("-release:17", "-Ybackend-parallelism", "1") else Seq("-release:17") }
Test / fork := true
Test / parallelExecution := false
Test / testFrameworks := Seq(new TestFramework("fixture.GroupFramework"))
Test / javaOptions += "-Dfixture.audit-root=" + sys.props("fixture.audit-root")
Global / concurrentRestrictions += Tags.limit(Tags.ForkedTestGroup, 1)
Test / testOptions := Def.uncached {
  val inherited = (Test / testOptions).value
  if (IO.read(file(sys.props("fixture.audit-root")) / "mode") == "empty") inherited :+ Tests.Exclude(Seq("fixture.SuiteA","fixture.SuiteB")) else inherited
}
Test / testGrouping := Def.uncached {
  val audit = file(sys.props("fixture.audit-root"))
  val mode = IO.read(audit / "mode")
  val definitions = (Test / definedTests).value.sortBy(_.name)
  val options = ForkOptions().withRunJVMOptions((Test / javaOptions).value.toVector)
  def group(name: String, tests: Seq[TestDefinition]) = new Tests.Group(name, tests, Tests.SubProcess(options))
  mode match {
    case "single" | "recovery" | "empty" => Seq(group("single", definitions))
    case "serial" | "halt" => definitions.map(test => group(test.name, Seq(test)))
    case "overlap" => Seq(group("first", definitions.filter(_.name == "fixture.SuiteA")), group("second", definitions.filter(_.name == "fixture.SuiteA")))
    case other => sys.error("Unknown group mode: " + other)
  }
}
Test / testSelected / testResultLogger := {
  val inherited = (Test / testSelected / testResultLogger).value
  val receiptParent = (Test / target).value / "distage-fork-receipts"
  new TestResultLogger {
    override def run(log: sbt.util.Logger, output: Tests.Output, taskName: String): Unit = {
      val audit = file(sys.props("fixture.audit-root"))
      IO.write(audit / "host.parent", ProcessHandle.current().pid().toString)
      val rows = output.events.toVector.sortBy(_._1).map { case (name,r) => Vector(name,r.result.toString,r.passedCount,r.failureCount,r.errorCount,r.skippedCount).mkString("\t") }
      IO.write(audit / "host.output", rows.mkString("\n"))
      val roots = (receiptParent * "command-*").get()
      require(roots.size == 1, "Command root missing")
      val entries = (roots.head * "fork-*.entered").get().sortBy(_.name)
      val entered = entries.map(file => IO.read(file))
      require(IO.read(audit / "mode") == "halt" || entered.forall(pid => ProcessHandle.of(pid.toLong).isPresent && ProcessHandle.of(pid.toLong).get.isAlive), "Fork exited before public result delivery")
      require((roots.head * "fork-*.decision").get().isEmpty, "Fork decision preceded public result delivery")
      IO.write(audit / "host.forks", entered.mkString("\n"))
      inherited.run(log,output,taskName)
    }
  }
}
val prepareGroups = inputKey[Unit]("Prepare one isolated grouping control")
prepareGroups := {
  val parsed = sbt.complete.DefaultParsers.spaceDelimited("mode").parsed
  require(parsed.size == 1,"Missing group mode")
  val audit = file(sys.props("fixture.audit-root"))
  IO.delete(audit); IO.createDirectory(audit)
  IO.delete((Test / target).value / "test-reports")
  IO.write(audit / "mode",parsed.head)
}
val rejectHalt = taskKey[Unit]("Observe premature worker death and permit same-session recovery")
rejectHalt := Def.uncached {
  (Test / testOnly).toTask(" fixture.SuiteA fixture.SuiteB").result.value.toEither match {
    case Left(cause) =>
      require(cause.toString.contains("Incomplete distage fork command acknowledgement"),"Unexpected halt failure: " + cause)
      IO.write(file(sys.props("fixture.audit-root")) / "host.rejected",cause.toString)
    case Right(_) => sys.error("HALT_FALSE_SUCCESS")
  }
}
val rejectOverlap = taskKey[Unit]("Observe duplicated-suite SDK aggregation loss")
rejectOverlap := Def.uncached {
  (Test / testOnly).toTask(" fixture.SuiteA fixture.SuiteB").result.value.toEither match {
    case Left(cause) =>
      require(cause.toString.contains("Incomplete distage host result"),"Unexpected overlap failure: " + cause)
      IO.write(file(sys.props("fixture.audit-root")) / "host.rejected",cause.toString)
    case Right(_) => sys.error("OVERLAP_FALSE_SUCCESS")
  }
}
val captureGroups = inputKey[Unit]("Freeze grouping output, bodies and reports")
captureGroups := {
  val parsed: Seq[String] = sbt.complete.DefaultParsers.spaceDelimited("case").parsed;
  Def.uncached {
    require(parsed.size == 1,"Missing capture case")
    val audit = file(sys.props("fixture.audit-root"))
    val destination = file(sys.props("fixture.captures")) / parsed.head
    require(!destination.exists(),"Capture must be new")
    IO.copyDirectory(audit,destination / "audit")
    IO.copyDirectory((Test / target).value / "test-reports",destination / "test-reports")
    val bodies = (audit * "*.body").get().map(file => IO.read(file))
    val pids = bodies.map(_.split("\t")(2)).distinct
    require(pids.forall(pid => !ProcessHandle.of(pid.toLong).isPresent || !ProcessHandle.of(pid.toLong).get.isAlive),"Worker survived public command")
    val roots = (Test / target).value / "distage-fork-receipts"
    require(roots.isDirectory && (roots * "*").get().isEmpty,"Command root survived cleanup")
    streams.value.log.info("FORK_COMMAND_GROUP_CAPTURE_OK case=" + parsed.head)
  }
}
'''

def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--repo-root', type=Path, required=True)
    parser.add_argument('--evidence-dir', type=Path, required=True)
    parser.add_argument('--artifact-version', required=True)
    parser.add_argument('--scala-version', choices=['3.9.0','2.13.18'], required=True)
    args = parser.parse_args()
    out = args.evidence_dir.resolve(); out.mkdir()
    shutil.copy2(__file__,out/'driver.py')
    build = out/'build'; (build/'project').mkdir(parents=True)
    source = build/'src/test/scala/GroupFramework.scala'; source.parent.mkdir(parents=True)
    source.write_text(SOURCE); (build/'build.sbt').write_text(BUILD)
    (build/'project/build.properties').write_text('sbt.version=2.0.9\n')
    (build/'project/plugins.sbt').write_text('addSbtPlugin("io.7mind.izumi" % "sbt-distage-testkit" % "'+args.artifact_version+'")\n')
    inputs = [dict(path=str(p),sha256=sha(p)) for p in sorted(build.rglob('*')) if p.is_file()]
    commands = ['set Global / localCacheDirectory := file("'+str(out/'local-cache')+'")']
    modes = ['single','serial','overlap','empty','halt','recovery']
    for mode in modes:
        request = 'rejectHalt' if mode == 'halt' else 'rejectOverlap' if mode == 'overlap' else 'testOnly fixture.SuiteA fixture.SuiteB'
        commands += ['prepareGroups '+mode,request,'captureGroups '+mode]
    commands += ['show Test / dependencyClasspath']
    argv = ['direnv','exec',str(args.repo_root.resolve()),'sh','-c','exec sbt --server --sbt-version 2.0.9 -java-home "$JDK21" -batch -J-Xmx6G "$@"','fork-command-groups','-Dfixture.scala-version='+args.scala_version,'-Dfixture.artifact-version='+args.artifact_version,'-Dfixture.audit-root='+str(build/'audit'),'-Dfixture.captures='+str(out/'cases'),*commands]
    (out/'commands.json').write_text(json.dumps(dict(cwd=str(build),argv=argv,inputs=inputs),indent=2)+'\n')
    print('FORK_COMMAND_GROUPS_START '+args.scala_version,flush=True)
    observation = None
    with (out/'run.log').open('x') as log:
        process = subprocess.Popen(argv,cwd=build,stdout=log,stderr=subprocess.STDOUT,start_new_session=True)
        deadline = time.monotonic() + TIMEOUT_SECONDS
        audit = build/'audit'
        while process.poll() is None and time.monotonic() < deadline:
            if observation is None and (audit/'worker.held').exists():
                time.sleep(5)
                observation = dict(pid=(audit/'worker.held').read_text(),rejectedBeforeRelease=(audit/'host.rejected').exists(),capturedBeforeRelease=(out/'cases/halt').exists())
                (out/'held-shutdown-observation.json').write_text(json.dumps(observation,indent=2)+'\n')
                (audit/'worker.allow').write_text('release after frozen shutdown observation\n')
            time.sleep(0.01)
        if observation is None and (audit/'worker.held').exists():
            observation = dict(pid=(audit/'worker.held').read_text(),rejectedBeforeRelease=(audit/'host.rejected').exists(),capturedBeforeRelease=(out/'cases/halt').exists())
            (out/'held-shutdown-observation.json').write_text(json.dumps(observation,indent=2)+'\n')
            (audit/'worker.allow').write_text('release after failed process observation\n')
        try: actual = process.wait(timeout=10)
        except subprocess.TimeoutExpired:
            os.killpg(process.pid,signal.SIGTERM)
            try: process.wait(timeout=10)
            except subprocess.TimeoutExpired: os.killpg(process.pid,signal.SIGKILL); process.wait()
            actual = 124
        log.write('\nEXIT '+str(actual)+'\n')
    failures = []; cases = []; all_pids = set(); parents = set()
    if observation is None or observation['rejectedBeforeRelease'] or observation['capturedBeforeRelease']: failures.append('Public command completed before held worker shutdown')
    if actual: failures.append('SBT failed: inspect run.log')
    else:
        for mode in modes:
            audit = out/'cases'/mode/'audit'
            rows = [p.read_text().split('\t') for p in sorted(audit.glob('*.body'))]
            pids = {r[2] for r in rows}
            if pids & all_pids: failures.append('Worker reused: '+mode)
            all_pids |= pids
            counts = {s:sum(r[0]==s for r in rows) for s in ['fixture.SuiteA','fixture.SuiteB']}
            expected = {'fixture.SuiteA':6,'fixture.SuiteB':0} if mode=='overlap' else {'fixture.SuiteA':0,'fixture.SuiteB':0} if mode=='empty' else {'fixture.SuiteA':3,'fixture.SuiteB':3}
            if counts != expected: failures.append('Body counts differ: '+mode)
            if len(pids) != (0 if mode=='empty' else 2 if mode in ['serial','overlap','halt'] else 1): failures.append('Fork membership differs: '+mode)
            reports = [ElementTree.parse(p) for p in (out/'cases'/mode/'test-reports').glob('*.xml')]
            xml_cases = [n for report in reports for n in report.findall('.//testcase')]
            output = (audit/'host.output').read_text() if (audit/'host.output').exists() else None
            rejected = (audit/'host.rejected').is_file()
            if mode in ['halt','overlap']:
                if not rejected: failures.append('Incomplete command accepted: '+mode)
            else:
                if rejected: failures.append('Successful control rejected: '+mode)
                parents.add((audit/'host.parent').read_text())
                entered = set((audit/'host.forks').read_text().splitlines())
                if entered != pids: failures.append('Admitted forks differ: '+mode)
                wanted = '\n'.join(s+'\tPassed\t'+str(n)+'\t0\t0\t0' for s,n in expected.items() if n)
                if output != wanted: failures.append('Public output differs: '+mode)
                if mode in ['single','serial','recovery'] and sorted((n.attrib['classname'],n.attrib['name']) for n in xml_cases) != [(s,'body-'+str(i)) for s in expected for i in range(1,4)]: failures.append('XML identities differ: '+mode)
                if mode == 'empty' and xml_cases: failures.append('Empty selection produced XML cases')
            cases.append(dict(mode=mode,bodies=len(rows),pids=sorted(pids),output=output,xmlCases=len(xml_cases),rejected=rejected))
        if len(parents)!=1: failures.append('Host session changed')
    for row in inputs: assert sha(row['path']) == row['sha256']
    result = dict(exit=0 if not failures else 1,actualExit=actual,scala=args.scala_version,heldShutdown=observation,cases=cases,failures=failures,scope='Serial and overlapping foreign groups, empty selection, exit-zero rejection and same-session recovery. Overlapping-suite Output/JUnit overwrite is explicitly rejected and remains an unresolved SDK reporting domain.')
    (out/'completion.json').write_text(json.dumps(result,indent=2)+'\n'); print(json.dumps(result),flush=True)
    raise SystemExit(result['exit'])

if __name__ == '__main__': main()
