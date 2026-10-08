#!/usr/bin/env python3
import json
from pathlib import Path
import traceback
from xml.etree import ElementTree

import sys
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from fixture_harness import write_sbt_project, freeze_driver, run_process, sha, scala_consumer_parser

TIMEOUT_SECONDS = 600
SHUTDOWN_GRACE_SECONDS = 10
SUITES = ['SuiteA', 'SuiteB', 'SuiteC', 'SuiteD', 'SuiteE']
MODES = ['normal', 'cancel', 'recovery', 'cancel-release-error', 'recovery']

SOURCE = r'''package fixture
import cats.effect.IO
import distage.DIKey
import izumi.distage.plugins.PluginConfig
import izumi.distage.testkit.model.{TestActivationStrategy, TestConfig}
import izumi.distage.testkit.runner.spec.Spec1
import izumi.logstage.api.Log
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths, StandardOpenOption}
object Audit {
  def directory: Path = Paths.get(@AUDIT@)
  def mode: String = new String(Files.readAllBytes(directory.resolve("mode")), StandardCharsets.UTF_8)
  def write(name: String, value: String): Unit = {
    val _ = Files.write(directory.resolve(name), value.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE_NEW)
  }
}
final class Resource(val id: String)
abstract class CancellationSuite extends Spec1[IO] {
  override protected def config: TestConfig = TestConfig.empty.copy(
    pluginConfig = PluginConfig.cached("fixture.plugins"),
    memoizationRoots = TestConfig.PriorityAxisDIKeys.fromSet(Set(DIKey[Resource])),
    activationStrategy = TestActivationStrategy.IgnoreConfig,
    parallelSuites = TestConfig.Parallelism.Sequential,
    parallelTests = TestConfig.Parallelism.Sequential,
    logLevel = Log.Level.Error,
  )
  private def body(index: Int, resource: Resource): IO[Unit] = IO {
    Audit.write(getClass.getName + "-" + index + ".body", Vector(getClass.getName, index.toString, resource.id, java.lang.ProcessHandle.current().pid().toString).mkString("\t"))
  }.flatMap { _ =>
    if (Audit.mode.startsWith("cancel")) IO.never[Unit].onCancel(IO { Audit.write("body.interrupted", java.lang.ProcessHandle.current().pid().toString) })
    else IO.unit
  }
  "same display name" should {
    "first" in { (resource: Resource) => body(1, resource) }
    "second" in { (resource: Resource) => body(2, resource) }
    "third" in { (resource: Resource) => body(3, resource) }
  }
}
'''
PLUGIN = r'''package fixture.plugins
import cats.effect.IO
import fixture.{Audit, Resource}
import izumi.distage.plugins.PluginDef
import izumi.functional.lifecycle.Lifecycle
import java.nio.file.Files
import java.util.UUID
final class CancellationPlugin extends PluginDef {
  private final val ReleaseTimeoutSeconds = 30L
  private final val PollMillis = 5L
  make[Resource].fromResource(() => Lifecycle.make[IO, Resource](IO {
    val id = UUID.randomUUID().toString
    Audit.write(id + ".acquire", id)
    new Resource(id)
  }) { resource => IO.blocking {
    Audit.write(resource.id + ".release-entered", resource.id)
    if (Audit.mode.startsWith("cancel")) {
      val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(ReleaseTimeoutSeconds)
      while (!Files.exists(Audit.directory.resolve("allow-release")) && System.nanoTime() < deadline) Thread.sleep(PollMillis)
      require(Files.exists(Audit.directory.resolve("allow-release")), "CANCEL_RELEASE_GATE_MISSING")
    }
    Audit.write(resource.id + ".release", resource.id)
    if (Audit.mode == "cancel-release-error") throw new IllegalStateException("CANCEL_RELEASE_FAILURE")
  } })
}
'''
FRAMEWORK = r'''package fixture
import sbt.testing.{EventHandler, Fingerprint, Framework, Logger, Runner, Task, TaskDef}
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
final class InterruptFramework extends Framework {
  private final val ControlTimeoutSeconds = 30L
  private final val PollMillis = 5L
  private final val ReleaseObservationMillis = 250L
  private val delegate = new izumi.distage.testkit.runner.bootstrap.Framework
  override def name(): String = delegate.name()
  override def fingerprints(): Array[Fingerprint] = delegate.fingerprints()
  override def runner(arguments: Array[String], remote: Array[String], loader: ClassLoader): Runner = {
    val original = delegate.runner(arguments, remote, loader)
    val controlled = new AtomicBoolean(false)
    new Runner {
      override def args(): Array[String] = original.args()
      override def remoteArgs(): Array[String] = original.remoteArgs()
      override def done(): String = original.done()
      override def tasks(definitions: Array[TaskDef]): Array[Task] = original.tasks(definitions.sortBy(_.fullyQualifiedName())).map { task => new Task {
        override def taskDef(): TaskDef = task.taskDef()
        override def tags(): Array[String] = task.tags()
        override def execute(handler: EventHandler, loggers: Array[Logger]): Array[Task] = {
          val owner = Audit.mode.startsWith("cancel") && controlled.compareAndSet(false, true)
          val executionThread = Thread.currentThread()
          val coordinator = if (owner) Some(new Thread(() => {
            def await(condition: => Boolean, label: String): Unit = {
              val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(ControlTimeoutSeconds)
              while (!condition && System.nanoTime() < deadline) Thread.sleep(PollMillis)
              require(condition, label)
            }
            def files(suffix: String): Vector[java.nio.file.Path] = {
              val entries = Files.list(Audit.directory)
              try {
                val values = Vector.newBuilder[java.nio.file.Path]
                entries.forEach { value => if (value.getFileName.toString.endsWith(suffix)) { val _ = values.addOne(value); () } }
                values.result()
              } finally entries.close()
            }
            try {
              await(files(".body").nonEmpty, "CANCEL_BODY_DID_NOT_ENTER")
              Audit.write("task.interrupt", java.lang.ProcessHandle.current().pid().toString)
              executionThread.interrupt()
              await(files(".release-entered").nonEmpty, "CANCEL_FINALIZER_DID_NOT_ENTER")
              Thread.sleep(ReleaseObservationMillis)
              require(!Files.exists(Audit.directory.resolve("task.returned")), "CANCEL_TASK_RETURNED_BEFORE_RELEASE")
              require(!files(".release").nonEmpty, "CANCEL_RELEASE_IGNORED_GATE")
              Audit.write("release.observed-held", "held")
            } catch {
              case cause: Throwable => Audit.write("control.failed", cause.toString)
            } finally Audit.write("allow-release", "release")
          }, "fixture-cancellation-control")) else None
          coordinator.foreach(_.start())
          try task.execute(handler, loggers)
          finally if (owner) {
            var interrupted = Thread.interrupted()
            try {
              Audit.write("task.returned", java.lang.ProcessHandle.current().pid().toString)
              coordinator.foreach { thread =>
                while (thread.isAlive) {
                  try thread.join() catch { case _: InterruptedException => interrupted = true }
                }
              }
            } finally if (interrupted) executionThread.interrupt()
          }
        }
      } }
    }
  }
}
'''
SETTINGS = r'''
import sbt.complete.DefaultParsers.spaceDelimited
lazy val cancellation = project.in(file(".")).enablePlugins(izumi.distage.sbt.DistageTestkitPlugin)
scalaVersion := @SCALA@
libraryDependencies += "io.7mind.izumi" %% "distage-testkit-runner" % @VERSION@ % Test
libraryDependencies += "org.typelevel" %% "cats-effect" % "3.7.1" % Test
libraryDependencies ++= { if (scalaVersion.value.startsWith("2.")) Seq(compilerPlugin("org.typelevel" % "kind-projector" % "0.13.4" cross CrossVersion.full)) else Seq.empty }
scalacOptions ++= { if (scalaVersion.value.startsWith("3.")) Seq("-release:17", "-Ybackend-parallelism", "1", "-Yretain-trees", "-Xmax-inlines:64", "-Xkind-projector:underscores") else Seq("-release:17", "-Xsource:3", "-P:kind-projector:underscore-placeholders") }
val proxyFramework = new TestFramework("fixture.InterruptFramework")
Test / testFrameworks := Seq(proxyFramework)
def proxyExecution(value: Tests.Execution): Tests.Execution = value.copy(options = value.options.map {
  case Tests.Argument(Some(framework), arguments) if framework == new TestFramework("izumi.distage.testkit.runner.bootstrap.Framework") => Tests.Argument(proxyFramework, arguments*)
  case other => other
})
Test / testSelected / testExecution := Def.uncached { proxyExecution((Test / testSelected / testExecution).value) }
Test / testQuick / testExecution := Def.uncached { proxyExecution((Test / testQuick / testExecution).value) }
Test / test / testExecution := Def.uncached { proxyExecution((Test / test / testExecution).value) }
lazy val prepareCancellation = inputKey[Unit]("Prepare one owned cancellation case")
prepareCancellation := {
  val modes = spaceDelimited("mode").parsed
  require(modes.size == 1, "Expected one mode")
  Def.uncached {
    val directory = baseDirectory.value / "audit"
    IO.delete(directory); IO.createDirectory(directory)
    IO.write(directory / "mode", modes.head)
    IO.write(directory / "host.pid", java.lang.ProcessHandle.current().pid().toString)
    IO.delete((Test / target).value / "test-reports")
    IO.delete((Test / target).value / "distage-events")
  }
}
lazy val observeCancellation = taskKey[Unit]("Observe failed cancellation without leaving SBT")
observeCancellation := Def.uncached {
  val result = (Test / testOnly).toTask(" fixture.Suite*").result.value
  require(result.toEither.isLeft, "CANCEL_FALSE_SUCCESS")
  IO.write(baseDirectory.value / "audit/host.rejected", result.toEither.left.toOption.get.toString)
}
lazy val captureCancellation = inputKey[Unit]("Capture body/resource/event/report evidence")
captureCancellation := {
  val names = spaceDelimited("name").parsed
  require(names.size == 1, "Expected one name")
  Def.uncached {
    val destination = file(@CAPTURES@) / names.head
    require(!destination.exists(), "Capture must be new")
    IO.copyDirectory(baseDirectory.value / "audit", destination / "audit")
    IO.copyDirectory((Test / target).value / "test-reports", destination / "test-reports")
    IO.copyDirectory((Test / target).value / "distage-events", destination / "events")
    val roots = (Test / target).value / "distage-fork-receipts"
    require(!roots.exists() || (roots * "*").get().isEmpty, "Command ownership survived completion")
  }
}
'''


def verify(directory, row, resources, runs):
    audit = directory / 'audit'
    bodies = [path.read_text().split('\t') for path in audit.glob('*.body')]
    cancelled = row['mode'].startswith('cancel')
    assert len(bodies) == (1 if cancelled else 15)
    expected = {(f'fixture.{suite}', str(index)) for suite in SUITES for index in range(1, 4)}
    if not cancelled:
        assert {(body[0], body[1]) for body in bodies} == expected
    acquired = [path.read_text() for path in audit.glob('*.acquire')]
    released = [path.read_text() for path in audit.glob('*.release')]
    assert len(acquired) == 1 and acquired == released and not resources.intersection(acquired)
    resources.update(acquired)
    assert all(body[2] == acquired[0] for body in bodies)
    parent = (audit / 'host.pid').read_text()
    pids = {body[3] for body in bodies}
    assert len(pids) == 1 and ((parent in pids) == (not row['fork']))
    if cancelled:
        assert (audit / 'task.interrupt').read_text() in pids
        assert (audit / 'body.interrupted').read_text() in pids
        assert (audit / 'release.observed-held').read_text() == 'held'
        assert (audit / 'task.returned').read_text() in pids
        assert not (audit / 'control.failed').exists()
        assert (audit / 'host.rejected').exists()
    else:
        assert not (audit / 'host.rejected').exists() and not (audit / 'task.interrupt').exists()
    paths = list((directory / 'events').glob('*.jsonl')); assert len(paths) == 1
    payload = paths[0].read_text(); assert payload.endswith('\n')
    envelopes = [json.loads(line) for line in payload.splitlines()]
    assert all(value['schemaVersion'] == 4 for value in envelopes)
    frames = [value['message'] for value in envelopes]
    assert frames[-1]['kind'] == 'completed'
    outcome = frames[-1]['outcome']; assert outcome['run'] not in runs; runs.add(outcome['run'])
    assert outcome['cancelled'] == cancelled
    results = outcome['results']
    assert len(results) == 15 and {(result['id']['suite'], tuple(result['id']['path'])) for result in results} == {(f'fixture.{suite}', ('same display name', 'should', leaf)) for suite in SUITES for leaf in ['first', 'second', 'third']}
    if cancelled:
        assert all(result['status'] == 'cancelled' for result in results)
        if row['mode'] == 'cancel-release-error':
            assert any(failure['phase'] == 'finalization' and 'CANCEL_RELEASE_FAILURE' in json.dumps(failure) for failure in outcome['failures'])
    else:
        assert all(result['status'] == 'succeeded' for result in results) and not outcome['failures']
    nodes = [node for path in (directory / 'test-reports').glob('*.xml') for node in ElementTree.parse(path).findall('.//testcase')]
    tests = [node for node in nodes if node.attrib['name'].startswith('same display name should ')]
    assert len(tests) == 15
    if cancelled:
        assert any(node.find('error') is not None for node in nodes)
    else:
        assert len(nodes) == 15 and all(node.find('error') is None and node.find('failure') is None for node in nodes)
    return dict(**row, bodies=len(bodies), acquired=acquired, released=released, hostPid=parent, bodyPids=sorted(pids), run=outcome['run'], cancelled=outcome['cancelled'], results=len(results), xmlCases=len(nodes), runFailures=outcome['failures'])


def main():
    args = scala_consumer_parser(multiple=False).parse_args()
    root = args.repo_root.resolve(); out = args.evidence_dir.resolve(); out.mkdir()
    freeze_driver(__file__, out / 'driver.py')
    build = out / 'build'
    source = build / 'src/test/scala'; source.mkdir(parents=True)
    audit = json.dumps(str(build / 'audit'))
    (source / 'Suites.scala').write_text(SOURCE.replace('@AUDIT@', audit) + '\n'.join(f'final class {suite} extends CancellationSuite' for suite in SUITES) + '\n')
    (source / 'Plugin.scala').write_text(PLUGIN)
    (source / 'Framework.scala').write_text(FRAMEWORK)
    write_sbt_project(build, SETTINGS.replace('@SCALA@', json.dumps(args.scala_version)).replace('@VERSION@', json.dumps(args.artifact_version)).replace('@CAPTURES@', json.dumps(str(out / 'cases'))), '2.0.9', 'addSbtPlugin("io.7mind.izumi" % "sbt-distage-testkit" % ' + json.dumps(args.artifact_version) + ')\n')
    rows = []; commands = []
    for fork in [False, True]:
        commands.append('set Test / fork := ' + str(fork).lower())
        for index, mode in enumerate(MODES):
            name = ('fork-' if fork else 'inprocess-') + str(index) + '-' + mode
            request = 'observeCancellation' if mode.startswith('cancel') else ('test' if index == 2 else 'testQuick') if mode == 'recovery' else 'testOnly fixture.Suite*'
            rows.append(dict(name=name, mode=mode, fork=fork, request=request))
            commands.extend(['prepareCancellation ' + mode, request, 'captureCancellation ' + name])
    inputs = [dict(path=str(path), sha256=sha(path)) for path in sorted(build.rglob('*')) if path.is_file()]
    argv = ['direnv', 'exec', str(root), 'sh', '-c', 'exec sbt --server --sbt-version 2.0.9 -java-home "$JDK21" -batch -J-Xmx6G "$@"', 'task-cancellation', *commands]
    (out / 'command.json').write_text(json.dumps(dict(argv=argv, cwd=str(build), inputs=inputs, cases=rows), indent=2) + '\n')
    with (out / 'run.log').open('w') as log:
        actual = run_process(argv, build, log, TIMEOUT_SECONDS, SHUTDOWN_GRACE_SECONDS)
    failures = []; checks = []; resources = set(); runs = set()
    if actual:
        failures.append('SBT failed: inspect run.log')
    else:
        for row in rows:
            try:
                checks.append(verify(out / 'cases' / row['name'], row, resources, runs))
            except (AssertionError, KeyError, ValueError) as cause:
                failures.append(row['name'] + ': ' + repr(cause) + '\n' + traceback.format_exc())
        assert len({row['hostPid'] for row in checks}) == 1
    changed = [row['path'] for row in inputs if sha(Path(row['path'])) != row['sha256']]
    result = dict(exit=int(bool(actual or failures or changed)), actualExit=actual, scala=args.scala_version, checks=checks, failures=failures, inputsChanged=changed)
    (out / 'completion.json').write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps(result), flush=True)
    raise SystemExit(result['exit'])


if __name__ == '__main__':
    main()
