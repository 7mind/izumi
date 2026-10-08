#!/usr/bin/env python3
import argparse
import json
from pathlib import Path
import traceback
from xml.etree import ElementTree

import sys
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from fixture_harness import write_sbt_project, freeze_driver, run_process, sha

TIMEOUT_SECONDS = 600
SHUTDOWN_GRACE_SECONDS = 10
MODES = ['normal', 'setup-failure', 'recovery', 'cleanup-failure', 'recovery']
LEAVES = ['first', 'second', 'third']

BODY = r'''
  private def record(index: Int): Unit = {
    val directory = java.nio.file.Paths.get(@AUDIT@)
    require(java.nio.file.Files.exists(directory.resolve("setup.hook")), "SETUP_NOT_OBSERVED_BEFORE_BODY")
    require(!java.nio.file.Files.exists(directory.resolve("cleanup.hook")), "CLEANUP_OBSERVED_BEFORE_BODY")
    val value = Vector(index.toString, java.lang.ProcessHandle.current().pid().toString, System.identityHashCode(getClass).toString).mkString("\t")
    val _ = java.nio.file.Files.write(directory.resolve(index + ".body"), value.getBytes(java.nio.charset.StandardCharsets.UTF_8), java.nio.file.StandardOpenOption.CREATE_NEW)
  }
'''
OWNED = r'''package fixture
final class SuiteA extends izumi.distage.testkit.runner.spec.AnyWordSpec {
@BODY@
  "same display name" should {
    "first" in record(1)
    "second" in record(2)
    "third" in record(3)
  }
}
'''
STOCK = r'''package fixture
import sbt.testing.{Event, EventHandler, Fingerprint, Framework, Logger, OptionalThrowable, Runner, Selector, Status, SubclassFingerprint, Task, TaskDef, TestSelector}
abstract class StockSuite { def runBodies(): Unit }
final class SuiteA extends StockSuite {
@BODY@
  override def runBodies(): Unit = (1 to 3).foreach(record)
}
final class StockFramework extends Framework {
  private val fingerprint = new SubclassFingerprint {
    override def isModule(): Boolean = false
    override def superclassName(): String = classOf[StockSuite].getName
    override def requireNoArgConstructor(): Boolean = true
  }
  override def name(): String = "stock-hook-control"
  override def fingerprints(): Array[Fingerprint] = Array(fingerprint)
  override def runner(arguments: Array[String], remote: Array[String], loader: ClassLoader): Runner = new Runner {
    override def args(): Array[String] = arguments
    override def remoteArgs(): Array[String] = remote
    override def done(): String = ""
    override def tasks(definitions: Array[TaskDef]): Array[Task] = definitions.map { definition => new Task {
      override def taskDef(): TaskDef = definition
      override def tags(): Array[String] = Array.empty[String]
      override def execute(handler: EventHandler, loggers: Array[Logger]): Array[Task] = {
        val suite = Class.forName(definition.fullyQualifiedName(), true, loader).getConstructor().newInstance().asInstanceOf[StockSuite]
        suite.runBodies()
        Vector("first", "second", "third").foreach { leaf => handler.handle(new Event {
          override def fullyQualifiedName(): String = definition.fullyQualifiedName()
          override def fingerprint(): Fingerprint = definition.fingerprint()
          override def selector(): Selector = new TestSelector("same display name should " + leaf)
          override def status(): Status = Status.Success
          override def throwable(): OptionalThrowable = new OptionalThrowable
          override def duration(): Long = 0L
        }) }
        Array.empty[Task]
      }
    } }
  }
}
'''

BUILD = r'''
import sbt.complete.DefaultParsers.spaceDelimited
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, StandardOpenOption}
lazy val prepareHooks = inputKey[Unit]("Prepare one owned callback case")
lazy val observeHooks = taskKey[Unit]("Record the public command result")
lazy val captureHooks = inputKey[Unit]("Capture callback/body/report evidence")
def hook(directory: File, name: String, loader: ClassLoader): Unit = {
  val mode = IO.read(directory / "mode")
  if (name == "cleanup") require((directory * "*.body").get().size == 3, "CLEANUP_RAN_BEFORE_ALL_BODY_EFFECTS")
  val loaded = try Some(loader.loadClass("fixture.SuiteA")) catch { case _: ClassNotFoundException => None }
  val value = Vector(name, java.lang.ProcessHandle.current().pid().toString, loader.getClass.getName, System.identityHashCode(loader).toString, loaded.map(value => System.identityHashCode(value).toString).getOrElse("unavailable"), System.nanoTime().toString).mkString("\t")
  val _ = Files.write((directory / (name + ".hook")).toPath, value.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE_NEW)
  if (mode == name + "-failure") throw new IllegalStateException("FIXTURE_" + name.toUpperCase + "_FAILURE")
}
lazy val common = Seq(
  scalaVersion := @SCALA@,
  libraryDependencies += "io.7mind.izumi" %% "distage-test-runner" % @VERSION@ % Test,
  scalacOptions ++= { if (scalaVersion.value.startsWith("3.")) Seq("-release:17", "-Ybackend-parallelism", "1") else Seq("-release:17") },
  Test / testOptions ++= Def.uncached {
    val directory = baseDirectory.value / "audit"
    val digest = sbt.util.Digest.sha256Hash(IO.read(baseDirectory.value.getParentFile / "build.sbt").getBytes(StandardCharsets.UTF_8))
    Seq(new Tests.Setup(loader => hook(directory, "setup", loader), digest), new Tests.Cleanup(loader => hook(directory, "cleanup", loader), digest))
  },
  prepareHooks := {
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
  },
  observeHooks := Def.uncached {
    val result = (Test / testOnly).toTask(" fixture.SuiteA").result.value
    val directory = baseDirectory.value / "audit"
    val mode = IO.read(directory / "mode")
    require(result.toEither.isRight == Set("normal", "recovery").contains(mode), "HOOK_RESULT_DIFFERS_FROM_MODE")
    IO.write(directory / "host.result", result.toEither.fold(value => "failed\n" + value.toString, _ => "passed"))
  },
  captureHooks := {
    val names = spaceDelimited("name").parsed
    require(names.size == 1, "Expected one name")
    Def.uncached {
      val destination = file(@CAPTURES@) / thisProjectRef.value.project / names.head
      require(!destination.exists(), "Capture must be new")
      IO.copyDirectory(baseDirectory.value / "audit", destination / "audit")
      IO.copyDirectory((Test / target).value / "test-reports", destination / "test-reports")
      val events = (Test / target).value / "distage-events"
      if (events.exists()) IO.copyDirectory(events, destination / "events")
      val roots = (Test / target).value / "distage-fork-receipts"
      require(!roots.exists() || (roots * "*").get().isEmpty, "Command ownership survived completion")
    }
  }
)
lazy val stock = project.in(file("stock")).settings(common).settings(Test / testFrameworks := Seq(new TestFramework("fixture.StockFramework")))
lazy val adapted = project.in(file("adapted")).enablePlugins(izumi.distage.sbt.DistageTestkitPlugin).settings(common)
'''





def verify(directory, row):
    audit = directory / 'audit'
    bodies = [path.read_text().split('\t') for path in sorted(audit.glob('*.body'))]
    hooks = {path.stem: path.read_text().split('\t') for path in audit.glob('*.hook')}
    parent = (audit / 'host.pid').read_text()
    assert set(hooks) == ({'setup'} if row['mode'] == 'setup-failure' else {'setup', 'cleanup'})
    assert all(fields[1] == parent for fields in hooks.values())
    assert [body[0] for body in bodies] == ([] if row['mode'] == 'setup-failure' else ['1', '2', '3'])
    if bodies:
        assert len({body[1] for body in bodies}) == 1
        assert (bodies[0][1] == parent) == (not row['fork'])
    if not row['fork']:
        assert hooks['setup'][4] != 'unavailable'
        assert all(body[2] == hooks['setup'][4] for body in bodies)
    if 'cleanup' in hooks:
        assert hooks['setup'][2:5] == hooks['cleanup'][2:5]
        assert int(hooks['setup'][5]) < int(hooks['cleanup'][5])
    verdict = (audit / 'host.result').read_text()
    assert verdict.startswith('passed') == (row['mode'] in ['normal', 'recovery'])
    if row['mode'].endswith('-failure'):
        assert 'FIXTURE_' + row['mode'].split('-')[0].upper() + '_FAILURE' in verdict
    nodes = [node for path in (directory / 'test-reports').glob('*.xml') for node in ElementTree.parse(path).findall('.//testcase')]
    tests = [node for node in nodes if node.attrib['name'].startswith('same display name should ')]
    expected = [] if row['mode'] == 'setup-failure' else [('fixture.SuiteA', 'same display name should ' + leaf) for leaf in LEAVES]
    assert sorted((node.attrib['classname'], node.attrib['name']) for node in tests) == expected
    assert all(node.find('error') is None and node.find('failure') is None for node in tests)
    if row['mode'] in ['normal', 'recovery']:
        assert len(nodes) == len(expected)
    return dict(**row, hooks={name: dict(pid=value[1], loaderClass=value[2], loaderId=value[3], suiteClass=value[4], timeNanos=value[5]) for name, value in hooks.items()}, bodies=len(bodies), bodyPids=sorted({value[1] for value in bodies}), xmlCases=len(nodes), hostPid=parent, verdict=verdict)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--repo-root', type=Path, required=True)
    parser.add_argument('--evidence-dir', type=Path, required=True)
    parser.add_argument('--artifact-version', required=True)
    parser.add_argument('--scala-version', choices=['3.9.0', '2.13.18'], required=True)
    args = parser.parse_args()
    root = args.repo_root.resolve(); out = args.evidence_dir.resolve(); out.mkdir()
    freeze_driver(__file__, out / 'driver.py')
    build = out / 'build'
    for project, template in [('stock', STOCK), ('adapted', OWNED)]:
        source = build / project / 'src/test/scala'; source.mkdir(parents=True)
        body = BODY.replace('@AUDIT@', json.dumps(str(build / project / 'audit')))
        (source / 'Suite.scala').write_text(template.replace('@BODY@', body))
    definition = BUILD.replace('@SCALA@', json.dumps(args.scala_version)).replace('@VERSION@', json.dumps(args.artifact_version)).replace('@CAPTURES@', json.dumps(str(out / 'cases')))
    write_sbt_project(build, definition, '2.0.9', 'addSbtPlugin("io.7mind.izumi" % "sbt-distage-testkit" % ' + json.dumps(args.artifact_version) + ')\n')
    rows = []; commands = []
    for project in ['stock', 'adapted']:
        commands.append('project ' + project)
        for fork in [False, True]:
            commands.append('set Test / fork := ' + str(fork).lower())
            for index, mode in enumerate(MODES):
                name = ('fork-' if fork else 'inprocess-') + str(index) + '-' + mode
                rows.append(dict(project=project, name=name, mode=mode, fork=fork))
                commands.extend(['prepareHooks ' + mode, 'observeHooks', 'captureHooks ' + name])
    inputs = [dict(path=str(path), sha256=sha(path)) for path in sorted(build.rglob('*')) if path.is_file()]
    argv = ['direnv', 'exec', str(root), 'sh', '-c', 'exec sbt --server --sbt-version 2.0.9 -java-home "$JDK21" -batch -J-Xmx6G "$@"', 'setup-cleanup', *commands]
    (out / 'command.json').write_text(json.dumps(dict(argv=argv, cwd=str(build), inputs=inputs, cases=rows), indent=2) + '\n')
    with (out / 'run.log').open('w') as log:
        actual = run_process(argv, build, log, TIMEOUT_SECONDS, SHUTDOWN_GRACE_SECONDS)
    failures = []; checks = []
    if actual:
        failures.append('SBT failed: inspect run.log')
    else:
        for row in rows:
            try:
                checks.append(verify(out / 'cases' / row['project'] / row['name'], row))
            except (AssertionError, KeyError, ValueError) as cause:
                failures.append(row['project'] + '/' + row['name'] + ': ' + repr(cause) + '\n' + traceback.format_exc())
        assert len({row['hostPid'] for row in checks}) == 1
        for mode_index, mode in enumerate(MODES):
            for fork in [False, True]:
                name = ('fork-' if fork else 'inprocess-') + str(mode_index) + '-' + mode
                pair = [row for row in checks if row['name'] == name]
                if len(pair) == 2:
                    assert pair[0]['bodies'] == pair[1]['bodies']
                    assert {key: value['suiteClass'] != 'unavailable' for key, value in pair[0]['hooks'].items()} == {key: value['suiteClass'] != 'unavailable' for key, value in pair[1]['hooks'].items()}
    changed = [row['path'] for row in inputs if sha(Path(row['path'])) != row['sha256']]
    result = dict(exit=int(bool(actual or failures or changed)), actualExit=actual, scala=args.scala_version, checks=checks, failures=failures, inputsChanged=changed)
    (out / 'completion.json').write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps(result), flush=True)
    raise SystemExit(result['exit'])


if __name__ == '__main__':
    main()
