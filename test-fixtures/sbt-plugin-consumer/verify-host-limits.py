#!/usr/bin/env python3
from pathlib import Path
import argparse
import hashlib
import json
import os
import re
import shutil
import signal
import subprocess
from xml.etree import ElementTree

ROOT = Path(__file__).resolve().parents[2]
FIXTURE = ROOT / 'test-fixtures/host-sharing-consumer'
LANE_TIMEOUT_SECONDS = 600
SHUTDOWN_GRACE_SECONDS = 10
SUITES = ['Suite' + letter for letter in 'ABCDE']
TESTS_PER_SUITE = 3
EXPECTED_BODIES = len(SUITES) * TESTS_PER_SUITE

WINDOW_SOURCE = '''package fixture

import sbt.{TestEvent, TestResult, TestsListener}
import java.nio.file.{Files, Path}

private[fixture] final case class SuiteName(value: String)

final class HostWindow(directory: Path, limit: Int, log: sbt.util.Logger) extends TestsListener {
  private val expected = "ABCDE".map(letter => SuiteName("izumi.fixtures.host.Suite" + letter)).toSet
  private final val TestsPerSuite = 3L
  private val expectedBodies = expected.size.toLong * TestsPerSuite
  private var started = Set.empty[SuiteName]
  private var ended = Set.empty[SuiteName]
  private var active = Set.empty[SuiteName]
  private var maximum = 0
  private var firstEndBodies = Option.empty[Long]

  override def doInit(): Unit = synchronized {
    require(active.isEmpty, "Host window initialized with active groups")
    started = Set.empty
    ended = Set.empty
    maximum = 0
    firstEndBodies = None
  }
  override def startGroup(name: String): Unit = synchronized {
    val suite = SuiteName(name)
    require(expected.contains(suite) && !started.contains(suite), "Unexpected or repeated host group: " + name)
    started += suite
    active += suite
    maximum = math.max(maximum, active.size)
    require(maximum <= limit, "Host group concurrency exceeds its configured limit")
    log.info("HOST_WINDOW_START suite=" + name + " active=" + active.size)
  }
  override def testEvent(event: TestEvent): Unit = { val _ = event; () }
  override def endGroup(name: String, cause: Throwable): Unit = throw new IllegalStateException("Unexpected host group failure: " + name, cause)
  override def endGroup(name: String, result: TestResult): Unit = synchronized {
    val suite = SuiteName(name)
    require(active.contains(suite) && !ended.contains(suite) && result == TestResult.Passed, "Unexpected host group completion: " + name)
    if (firstEndBodies.isEmpty) {
      val files = Files.list(directory)
      try firstEndBodies = Some(files.filter(path => path.getFileName.toString.endsWith(".body")).count())
      finally files.close()
      require(firstEndBodies.contains(expectedBodies), "First returning host task did not complete all fifteen shared bodies")
    }
    active -= suite
    ended += suite
    log.info("HOST_WINDOW_END suite=" + name + " active=" + active.size)
  }
  override def doComplete(result: TestResult): Unit = synchronized {
    require(result == TestResult.Passed && active.isEmpty && started == expected && ended == expected, "Incomplete host window")
    require(firstEndBodies.contains(expectedBodies), "First task body evidence is absent")
    log.info("HOST_WINDOW_OK limit=" + limit + " maximum=" + maximum + " started=" + started.size + " ended=" + ended.size + " firstEndBodies=" + firstEndBodies.get)
  }
}
'''


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--artifact-version', required=True)
    parser.add_argument('--sbt-version', nargs='+', required=True, choices=['2.0.9', '1.13.0'])
    parser.add_argument('--scala-version', nargs='+', required=True, choices=['3.9.0', '2.13.18', '2.12.21'])
    parser.add_argument('--host-threads', nargs='+', required=True, type=int, choices=[1, 2])
    parser.add_argument('--evidence-dir', required=True, type=Path)
    arguments = parser.parse_args()
    evidence = arguments.evidence_dir.resolve()
    evidence.mkdir(parents=True, exist_ok=False)
    sources = [Path(__file__).resolve(), FIXTURE / 'build.sbt', *sorted((FIXTURE / 'src').rglob('*.scala'))]
    inputs = []
    for path in sources:
        frozen = evidence / 'sources' / path.relative_to(ROOT)
        frozen.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(path, frozen)
        inputs.append(dict(path=str(path), frozen=str(frozen), sha256=sha(path)))
    (evidence / 'inputs.json').write_text(json.dumps(dict(sources=inputs), indent=2) + '\n')
    outcomes = []
    resources = set()
    for sdk in arguments.sbt_version:
        for scala in arguments.scala_version:
            for limit in arguments.host_threads:
                lane = evidence / ('sbt' + sdk + '-scala' + scala + '-threads' + str(limit))
                build = lane / 'build'
                build.mkdir(parents=True)
                for row in inputs:
                    original = Path(row['path'])
                    if original == Path(__file__).resolve():
                        continue
                    path = build / original.relative_to(FIXTURE)
                    path.parent.mkdir(parents=True, exist_ok=True)
                    source = Path(row['frozen']).read_text()
                    if original.name == 'build.sbt':
                        start = source.index('Test / testFrameworks :=')
                        end = source.index('Test / javaOptions +=', start)
                        source = source[:start] + source[end:]
                        before = 'val diLabels = Set("SuiteC", "SuiteD", "SuiteE")'
                        assert source.count(before) == 1
                        source = source.replace(before, 'val diLabels = Set("SuiteA", "SuiteB", "SuiteC", "SuiteD", "SuiteE")')
                        source += '\nlazy val sharingConsumer = project.in(file(".")).enablePlugins(izumi.distage.sbt.DistageTestkitPlugin)\n'
                        source += 'Global / concurrentRestrictions := Seq(Tags.limitAll(' + str(limit) + '))\n'
                        source += 'Test / parallelExecution := true\n'
                        source += 'Test / testListeners += new fixture.HostWindow(file(sys.props("izumi.fixture.audit-root")).toPath, ' + str(limit) + ', streams.value.log)\n'
                    elif original.name in ['SuiteA.scala', 'SuiteB.scala']:
                        assert source.count('extends PlainFixtureSuite') == 1
                        source = source.replace('extends PlainFixtureSuite', 'extends DIFixtureSuite')
                    path.write_text(source)
                project = build / 'project'
                project.mkdir()
                (project / 'build.properties').write_text('sbt.version=' + sdk + '\n')
                (project / 'plugins.sbt').write_text('addSbtPlugin("io.7mind.izumi" % "sbt-distage-testkit" % "' + arguments.artifact_version + '")\n')
                (project / 'HostWindow.scala').write_text(WINDOW_SOURCE)
                full = 'testFull' if sdk == '2.0.9' else 'test'
                quick = 'test' if sdk == '2.0.9' else 'testQuick'
                sequence = ['show Global / concurrentRestrictions', 'show Test / parallelExecution', 'prepareFixture full', full, 'verifyFixture full 1 ' + ' '.join(SUITES), 'prepareFixture repeat', quick, 'verifyFixture repeat 1 ' + ' '.join(SUITES), 'show Test / dependencyClasspath', 'show Test / fullClasspath']
                if sdk == '2.0.9':
                    sequence.insert(0, 'set Global / localCacheDirectory := file("' + str(lane / 'local-cache') + '")')
                shell = 'task_sdk="$1"; shift; exec sbt --server --sbt-version "$task_sdk" -java-home "$JDK21" -batch -J-Xmx6G "$@"'
                argv = ['direnv', 'exec', str(ROOT), 'sh', '-c', shell, 'five-suite-host-limits', sdk,
                        '-Dizumi.fixture.scala-version=' + scala, '-Dizumi.fixture.version=' + arguments.artifact_version,
                        '-Dizumi.fixture.audit-root=' + str(build / 'target/body-audit'),
                        '-Dizumi.fixture.captures=' + str(lane / 'cases'), *sequence]
                generated = [dict(path=str(path), sha256=sha(path)) for path in sorted(build.rglob('*')) if path.is_file()]
                (lane / 'commands.json').write_text(json.dumps(dict(cwd=str(build), argv=argv, inputs=generated), indent=2) + '\n')
                print('HOST_LIMIT_LANE_START ' + str(lane), flush=True)
                with (lane / 'run.log').open('x') as log:
                    process = subprocess.Popen(argv, cwd=build, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
                    try:
                        code = process.wait(timeout=LANE_TIMEOUT_SECONDS)
                    except subprocess.TimeoutExpired:
                        os.killpg(process.pid, signal.SIGTERM)
                        try:
                            process.wait(timeout=SHUTDOWN_GRACE_SECONDS)
                        except subprocess.TimeoutExpired:
                            os.killpg(process.pid, signal.SIGKILL)
                            process.wait()
                        code = 124
                    log.write('\nEXIT ' + str(code) + '\n')
                raw = (lane / 'run.log').read_text()
                windows = re.findall(r'^\[info\] HOST_WINDOW_OK limit=(\d+) maximum=(\d+) started=5 ended=5 firstEndBodies=15$', raw, re.M)
                failures = []
                if code != 0 or len(windows) != 2 or any(int(cap) != limit or not 1 <= int(actual) <= limit for cap, actual in windows):
                    failures.append('Host window completion/control differs')
                for case in ['full', 'repeat']:
                    capture = lane / 'cases' / case
                    audit = capture / 'body-audit'
                    bodies = [path.read_text().split('\t') for path in audit.glob('*.body')]
                    acquired = [path.read_text().strip() for path in audit.glob('*.acquire')]
                    released = [path.read_text().strip() for path in audit.glob('*.release')]
                    expected = [("izumi.fixtures.host." + suite, str(index)) for suite in SUITES for index in range(1, TESTS_PER_SUITE + 1)]
                    identities = []
                    reports = sorted((capture / 'test-reports').glob('*.xml'))
                    for path in reports:
                        xml = ElementTree.parse(path)
                        suite = xml.getroot()
                        if suite.attrib['tests'] != str(TESTS_PER_SUITE) or any(suite.attrib[kind] != '0' for kind in ['errors', 'failures', 'skipped']):
                            failures.append('XML suite totals differ: ' + case)
                        if any(xml.findall('.//' + kind) for kind in ['error', 'failure', 'skipped']):
                            failures.append('Unexpected XML outcome: ' + case)
                        identities.extend((node.attrib['classname'], node.attrib['name']) for node in xml.findall('.//testcase'))
                    expected_xml = [('izumi.fixtures.host.' + suite, 'equal display name should ' + leaf) for suite in SUITES for leaf in ['first', 'second', 'third']]
                    if sorted((row[0], row[1]) for row in bodies) != sorted(expected) or sorted(identities) != sorted(expected_xml):
                        failures.append('Physical/report identity set differs: ' + case)
                    if len(acquired) != 1 or acquired != released or {row[2] for row in bodies} != set(acquired) or resources.intersection(acquired):
                        failures.append('Shared/fresh paired resource lifetime differs: ' + case)
                    resources.update(acquired)
                for row in inputs:
                    assert sha(row['path']) == sha(row['frozen']) == row['sha256']
                for row in generated:
                    assert sha(row['path']) == row['sha256']
                outcome = dict(sbt=sdk, scala=scala, threads=limit, actualExit=code, windows=windows, validationFailures=failures)
                (lane / 'completion.json').write_text(json.dumps(outcome, indent=2) + '\n')
                outcomes.append(outcome)
                print(json.dumps(outcome), flush=True)
                if failures:
                    (evidence / 'completion.json').write_text(json.dumps(dict(exit=1, outcomes=outcomes), indent=2) + '\n')
                    raise SystemExit(1)
    (evidence / 'completion.json').write_text(json.dumps(dict(exit=0, outcomes=outcomes, bodies=len(outcomes) * 2 * EXPECTED_BODIES, resourceLifetimes=len(resources), scope='Five compatible DI suites, actual SBT host task windows of one/two and serial fresh-resource recovery; in-process JVM controls, no cancellation/fork/multi-project acceptance.'), indent=2) + '\n')


if __name__ == '__main__':
    main()
