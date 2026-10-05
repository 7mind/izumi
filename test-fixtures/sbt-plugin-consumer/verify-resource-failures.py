#!/usr/bin/env python3
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import signal
import subprocess
from xml.etree import ElementTree

TIMEOUT_SECONDS = 600
SHUTDOWN_GRACE_SECONDS = 10
SUITES = ['SuiteA', 'SuiteB', 'SuiteC', 'SuiteD', 'SuiteE']
LEAVES = ['first', 'second', 'third']
MODES = ['normal', 'acquire', 'recovery', 'assertion', 'recovery', 'body', 'recovery', 'release', 'recovery']

SOURCE = r'''package fixture
import distage.DIKey
import izumi.distage.plugins.PluginConfig
import izumi.distage.testkit.model.{TestActivationStrategy, TestConfig}
import izumi.distage.testkit.runner.spec.SpecIdentity
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths, StandardOpenOption}

object Audit {
  def directory: Path = Paths.get(sys.props("fixture.audit-root"))
  def mode: String = new String(Files.readAllBytes(directory.resolve("mode")), StandardCharsets.UTF_8)
  def write(name: String, value: String): Unit = {
    val _ = Files.write(directory.resolve(name), value.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE_NEW)
  }
}
final class Resource(val id: String)
abstract class FailureSuite extends SpecIdentity {
  override protected def config: TestConfig = TestConfig.empty.copy(
    pluginConfig = PluginConfig.cached("fixture.plugins"),
    memoizationRoots = TestConfig.PriorityAxisDIKeys.fromSet(Set(DIKey[Resource])),
    activationStrategy = TestActivationStrategy.IgnoreConfig,
  )
  private def body(index: Int, resource: Resource): Unit = {
    Audit.write(getClass.getName + "-" + index + ".body", Vector(getClass.getName, index.toString, resource.id, java.lang.ProcessHandle.current().pid().toString).mkString("\t"))
    if (getClass.getName == "fixture.SuiteC" && index == 1) {
      if (Audit.mode == "body") throw new IllegalStateException("FIXTURE_BODY_FAILURE")
      if (Audit.mode == "assertion") assert(false)
    }
  }
  "same display name" should {
    "first" in { (resource: Resource) => body(1, resource) }
    "second" in { (resource: Resource) => body(2, resource) }
    "third" in { (resource: Resource) => body(3, resource) }
  }
}
'''

PLUGIN = r'''package fixture.plugins
import fixture.{Audit, Resource}
import izumi.distage.plugins.PluginDef
import izumi.functional.lifecycle.Lifecycle
import izumi.fundamentals.platform.functional.Identity
import java.util.UUID
final class FailurePlugin extends PluginDef {
  make[Resource].fromResource(() => Lifecycle.make[Identity, Resource] {
    val id = UUID.randomUUID().toString
    Audit.write(id + ".attempt", id)
    if (Audit.mode == "acquire") throw new IllegalStateException("FIXTURE_ACQUIRE_FAILURE")
    Audit.write(id + ".acquire", id)
    new Resource(id)
  } { resource =>
    Audit.write(resource.id + ".release", resource.id)
    if (Audit.mode == "release") throw new IllegalStateException("FIXTURE_RELEASE_FAILURE")
  })
}
'''

SETTINGS = r'''
import sbt.complete.DefaultParsers.spaceDelimited
lazy val failures = project.in(file(".")).enablePlugins(izumi.distage.sbt.DistageTestkitPlugin)
scalaVersion := @SCALA@
libraryDependencies += "io.7mind.izumi" %% "distage-testkit-runner" % @VERSION@ % Test
libraryDependencies ++= { if (scalaVersion.value.startsWith("2.")) Seq(compilerPlugin("org.typelevel" % "kind-projector" % "0.13.4" cross CrossVersion.full)) else Seq.empty }
scalacOptions ++= { if (scalaVersion.value.startsWith("3.")) Seq("-release:17", "-Ybackend-parallelism", "1", "-Yretain-trees", "-Xmax-inlines:64", "-Xkind-projector:underscores") else Seq("-release:17", "-Xsource:3", "-P:kind-projector:underscore-placeholders") }
target := baseDirectory.value / "target"
Test / target := baseDirectory.value / "target/test"
Test / javaOptions += @AUDIT_OPTION@
lazy val prepareFailure = inputKey[Unit]("Prepare an owned failure case")
prepareFailure := {
  val mode = spaceDelimited("mode").parsed
  require(mode.size == 1, "Expected one mode")
  Def.uncached {
    val audit = baseDirectory.value / "target/audit"
    IO.delete(audit); IO.createDirectory(audit)
    IO.write(audit / "mode", mode.head)
    IO.write(audit / "host.pid", java.lang.ProcessHandle.current().pid().toString)
    IO.delete((Test / target).value / "test-reports")
    IO.delete((Test / distageEventDirectory).value)
  }
}
lazy val observeFailure = taskKey[Unit]("Observe rejection without leaving the SBT session")
observeFailure := Def.uncached {
  val result = (Test / testOnly).toTask(" fixture.Suite*").result.value
  require(result.toEither.isLeft, "FAILURE_FALSE_SUCCESS")
  IO.write(baseDirectory.value / "target/audit/host.rejected", result.toEither.left.toOption.get.toString)
}
lazy val captureFailure = inputKey[Unit]("Capture physical and public outcomes")
captureFailure := {
  val name = spaceDelimited("name").parsed
  require(name.size == 1, "Expected one capture name")
  Def.uncached {
    val destination = file(@CAPTURES@) / name.head
    require(!destination.exists(), "Capture must be new")
    IO.copyDirectory(baseDirectory.value / "target/audit", destination / "audit")
    IO.copyDirectory((Test / target).value / "test-reports", destination / "test-reports")
    IO.copyDirectory((Test / distageEventDirectory).value, destination / "events")
    val roots = (Test / target).value / "distage-fork-receipts"
    require(!roots.exists() || (roots * "*").get().isEmpty, "Command ownership survived completion")
  }
}
'''


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def test_identity(value):
    return value['suite'], tuple(value['path'])


def verify(case, row, resources, runs, parents):
    audit = case / 'audit'
    bodies = [path.read_text().split('\t') for path in audit.glob('*.body')]
    expected = {(f'fixture.{suite}', str(index)) for suite in SUITES for index in range(1, 4)}
    assert {(body[0], body[1]) for body in bodies} == (set() if row['mode'] == 'acquire' else expected)
    assert len(bodies) == (0 if row['mode'] == 'acquire' else len(expected))
    attempts = [path.read_text() for path in audit.glob('*.attempt')]
    acquired = [path.read_text() for path in audit.glob('*.acquire')]
    released = [path.read_text() for path in audit.glob('*.release')]
    assert len(attempts) == 1 and not resources.intersection(attempts)
    resources.update(attempts)
    assert acquired == released and len(acquired) == int(row['mode'] != 'acquire')
    assert all(body[2] in acquired for body in bodies)
    parent = int((audit / 'host.pid').read_text()); parents.add(parent)
    pids = {int(body[3]) for body in bodies}
    if bodies:
        assert len(pids) == 1 and ((parent in pids) == (not row['fork']))
    paths = list((case / 'events').glob('*.jsonl'))
    assert len(paths) == 1
    payload = paths[0].read_text()
    assert payload.endswith('\n')
    envelopes = [json.loads(line) for line in payload.splitlines()]
    assert all(frame['schemaVersion'] == 4 for frame in envelopes)
    frames = [frame['message'] for frame in envelopes]
    assert frames[-1]['kind'] == 'completed'
    outcome = frames[-1]['outcome']; run = outcome['run']
    assert run not in runs and paths[0].stem == run; runs.add(run)
    events = [frame for frame in frames if frame['kind'] == 'event']
    assert [int(frame['sequence']) for frame in events] == list(range(len(events)))
    assert all(frame['event']['run'] == run for frame in events)
    assert events[-1]['event']['kind'] == 'finished' and events[-1]['event']['outcome'] == outcome
    terminal = [frame['event']['result'] for frame in events if frame['event']['kind'] == 'testCompleted']
    expected_ids = {(f'fixture.{suite}', ('same display name', 'should', leaf)) for suite in SUITES for leaf in LEAVES}
    assert len(terminal) == len(expected_ids) and {test_identity(result['id']) for result in terminal} == expected_ids
    assert {test_identity(result['id']) for result in outcome['results']} == expected_ids
    failed = [result for result in terminal if result['status'] != 'succeeded']
    if row['mode'] in ['normal', 'recovery']:
        assert not failed and not outcome['failures'] and not outcome['cancelled']
        assert not (audit / 'host.rejected').exists()
    else:
        assert (audit / 'host.rejected').exists()
        sentinel = 'FIXTURE_' + row['mode'].upper() + '_FAILURE'
        if row['mode'] != 'assertion':
            assert sentinel in json.dumps(outcome)
        if row['mode'] in ['assertion', 'body']:
            assert len(failed) == 1 and test_identity(failed[0]['id']) == ('fixture.SuiteC', ('same display name', 'should', 'first'))
            assert failed[0]['status'] == 'failed' and failed[0]['failure']['phase'] == 'test'
            assert (failed[0]['failure']['assertion'] is not None) == (row['mode'] == 'assertion')
            assert not outcome['failures'] and not outcome['cancelled']
        elif row['mode'] == 'release':
            assert not failed and len(outcome['failures']) == 1 and outcome['failures'][0]['phase'] == 'finalization'
        else:
            assert len(failed) == len(expected_ids)
            assert all(result['failure'] is not None and result['failure']['phase'] == 'setup' for result in failed)
    nodes = [node for path in (case / 'test-reports').glob('*.xml') for node in ElementTree.parse(path).findall('.//testcase')]
    test_nodes = [node for node in nodes if node.attrib['name'].startswith('same display name should ')]
    assert len(test_nodes) == len(expected_ids)
    assert {(node.attrib['classname'], tuple(node.attrib['name'].split(' '))) for node in test_nodes} == {(suite, tuple(' '.join(path).split(' '))) for suite, path in expected_ids}
    errors = [node for node in nodes if node.find('error') is not None]
    failures = [node for node in nodes if node.find('failure') is not None]
    if row['mode'] == 'release':
        assert len(errors) == len(SUITES) and not failures and len(nodes) == len(expected_ids) + len(SUITES)
        assert all('FIXTURE_RELEASE_FAILURE' in ElementTree.tostring(node, encoding='unicode') for node in errors)
    elif row['mode'] in ['assertion', 'body']:
        assert len(failures) == 1 and not errors and len(nodes) == len(expected_ids)
    elif row['mode'] in ['normal', 'recovery']:
        assert not errors and not failures and len(nodes) == len(expected_ids)
    return dict(case=row['name'], mode=row['mode'], fork=row['fork'], bodies=len(bodies), results=len(terminal), xmlCases=len(nodes), failures=len(failures), errors=len(errors), phases=sorted({result['failure']['phase'] for result in failed if result['failure'] is not None} | {failure['phase'] for failure in outcome['failures']}), hostPid=parent, bodyPids=sorted(pids), run=run, resource=attempts[0])


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--repo-root', type=Path, required=True)
    parser.add_argument('--evidence-dir', type=Path, required=True)
    parser.add_argument('--artifact-version', required=True)
    parser.add_argument('--scala-version', choices=['3.9.0', '2.13.18'], required=True)
    args = parser.parse_args()
    root = args.repo_root.resolve(); out = args.evidence_dir.resolve(); out.mkdir()
    shutil.copy2(__file__, out / 'driver.py')
    build = out / 'build'; (build / 'project').mkdir(parents=True)
    source = build / 'src/test/scala'; source.mkdir(parents=True)
    (source / 'Suites.scala').write_text(SOURCE + '\n'.join(f'final class {suite} extends FailureSuite' for suite in SUITES) + '\n')
    (source / 'Plugin.scala').write_text(PLUGIN)
    definition = SETTINGS.replace('@SCALA@', json.dumps(args.scala_version)).replace('@VERSION@', json.dumps(args.artifact_version)).replace('@AUDIT_OPTION@', json.dumps('-Dfixture.audit-root=' + str(build / 'target/audit'))).replace('@CAPTURES@', json.dumps(str(out / 'cases')))
    (build / 'build.sbt').write_text(definition)
    (build / 'project/build.properties').write_text('sbt.version=2.0.9\n')
    (build / 'project/plugins.sbt').write_text('addSbtPlugin("io.7mind.izumi" % "sbt-distage-testkit" % ' + json.dumps(args.artifact_version) + ')\n')
    rows = []; commands = []
    for fork in [False, True]:
        commands.append('set Test / fork := ' + str(fork).lower())
        for index, mode in enumerate(MODES):
            name = ('fork-' if fork else 'inprocess-') + str(index) + '-' + mode
            request = 'testOnly fixture.Suite*' if mode in ['normal', 'recovery'] else 'observeFailure'
            if mode == 'recovery':
                request = 'test' if index in [2, 6] else 'testQuick'
            rows.append(dict(name=name, mode=mode, fork=fork, request=request))
            commands.extend(['prepareFailure ' + mode, request, 'captureFailure ' + name])
    inputs = [dict(path=str(path), sha256=sha(path)) for path in sorted(build.rglob('*')) if path.is_file()]
    argv = ['direnv', 'exec', str(root), 'sh', '-c', 'exec sbt --server --sbt-version 2.0.9 -java-home "$JDK21" -batch -J-Xmx6G "$@"', 'resource-failures', '-Dfixture.audit-root=' + str(build / 'target/audit'), *commands]
    (out / 'command.json').write_text(json.dumps(dict(argv=argv, cwd=str(build), inputs=inputs, cases=rows), indent=2) + '\n')
    with (out / 'run.log').open('w') as log:
        process = subprocess.Popen(argv, cwd=build, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            actual = process.wait(timeout=TIMEOUT_SECONDS)
        except subprocess.TimeoutExpired:
            os.killpg(process.pid, signal.SIGTERM)
            try:
                process.wait(timeout=SHUTDOWN_GRACE_SECONDS)
            except subprocess.TimeoutExpired:
                os.killpg(process.pid, signal.SIGKILL); process.wait()
            actual = 124
    failures = []; checks = []; resources = set(); runs = set(); parents = set()
    if actual:
        failures.append('SBT process failed: inspect run.log')
    else:
        for row in rows:
            try:
                checks.append(verify(out / 'cases' / row['name'], row, resources, runs, parents))
            except (AssertionError, KeyError, ValueError) as cause:
                failures.append(row['name'] + ': ' + repr(cause))
        assert len(parents) == 1
    changed = [row['path'] for row in inputs if sha(Path(row['path'])) != row['sha256']]
    result = dict(exit=int(bool(actual or failures or changed)), actualExit=actual, scala=args.scala_version, checks=checks, failures=failures, inputsChanged=changed)
    (out / 'completion.json').write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps(result), flush=True)
    raise SystemExit(result['exit'])


if __name__ == '__main__':
    main()
