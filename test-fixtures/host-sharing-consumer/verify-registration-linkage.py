#!/usr/bin/env python3
from pathlib import Path
from xml.etree import ElementTree
import argparse
import json

import sys
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from fixture_harness import freeze_driver, run_process, sha, freeze_sources

ROOT = Path(__file__).resolve().parents[2]
TIMEOUT_SECONDS = 300
GRACE_SECONDS = 10
PREFIX = 'izumi.fixtures.host.'
HEALTHY = PREFIX + 'SuiteC'
FAULT = PREFIX + 'RegistrationLinkageSuite'
SOURCE = r'''package izumi.fixtures.host
import izumi.distage.testkit.runner.{RegisteredSuite, RegistrationContext, TestSuite}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths, StandardOpenOption}
final class RegistrationLinkageSuite extends TestSuite {
  override def register(context: RegistrationContext): RegisteredSuite = {
    val _ = context
    val pid = ProcessHandle.current().pid().toString
    val written = Files.write(Paths.get(sys.props("izumi.fixture.audit-root")).resolve("registration.pid"), pid.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
    require(Files.isRegularFile(written), "Registration entry was not recorded")
    println("REGISTRATION_CONSUMER_THROW pid=" + pid)
    throw new NoClassDefFoundError("fixture-registration-missing-dependency")
  }
}
'''
SETTINGS = r'''
lazy val registrationConsumer = project.in(file(".")).enablePlugins(izumi.distage.sbt.DistageTestkitPlugin)
Test / fork := true
Test / testSelected / testResultLogger := {
  val inherited = (Test / testSelected / testResultLogger).value
  val audit = file(sys.props("izumi.fixture.audit-root"))
  new TestResultLogger {
    override def run(log: sbt.util.Logger, output: Tests.Output, taskName: String): Unit = {
      val rows = output.events.toVector.sortBy(_._1).map { case (name, result) =>
        Vector(name, result.result.toString, result.passedCount.toString, result.failureCount.toString, result.errorCount.toString, result.skippedCount.toString, result.ignoredCount.toString, result.canceledCount.toString, result.pendingCount.toString).mkString("\t")
      }
      IO.write(audit / "host.output", rows.mkString("\n"))
      IO.write(audit / "host.parent-pid", ProcessHandle.current().pid().toString)
      inherited.run(log, output, taskName)
    }
  }
}
val registrationFailure = taskKey[Unit]("Observe the failed public testOnly task before recovery")
registrationFailure := Def.uncached {
  (Test / testOnly).toTask(" izumi.fixtures.host.RegistrationLinkageSuite izumi.fixtures.host.SuiteC").result.value.toEither match {
    case Left(cause) => streams.value.log.info("REGISTRATION_CONSUMER_TASK_REJECTED " + cause.toString)
    case Right(_) => sys.error("REGISTRATION_CONSUMER_FALSE_SUCCESS")
  }
}
val captureRegistrationFixture = inputKey[Unit]("Freeze one completed registration failure or recovery command")
captureRegistrationFixture := {
  val parsed: Seq[String] = spaceDelimited("case").parsed;
  Def.uncached {
    require(parsed.size == 1, "Registration capture requires a case")
    val destination = file(sys.props("izumi.fixture.captures")) / parsed.head
    require(!destination.exists(), "Registration capture must be new")
    IO.copyDirectory(file(sys.props("izumi.fixture.audit-root")), destination / "body-audit")
    IO.copyDirectory((Test / target).value / "test-reports", destination / "test-reports")
    val receipts = (Test / target).value / "distage-fork-receipts"
    require(receipts.isDirectory && (receipts * "*").get().isEmpty, "Registration command receipt survived cleanup")
    streams.value.log.info("REGISTRATION_CONSUMER_CAPTURE_OK case=" + parsed.head)
  }
}
'''


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--evidence-dir', type=Path, required=True)
    parser.add_argument('--artifact-version', required=True)
    parser.add_argument('--scala-version', choices=['3.9.0', '2.13.18'], required=True)
    args = parser.parse_args()
    output = args.evidence_dir.resolve()
    output.mkdir(exist_ok=False)
    freeze_driver(__file__, output / 'driver.py')
    fixture = ROOT / 'test-fixtures/host-sharing-consumer'
    originals = [fixture / 'build.sbt', *sorted((fixture / 'src').rglob('*.scala'))]
    build = output / 'build'
    build.mkdir()
    inputs = []
    for source in originals:
        destination = build / source.relative_to(fixture)
        destination.parent.mkdir(parents=True, exist_ok=True)
        value = source.read_text()
        if source.name == 'build.sbt':
            start = value.index('Test / testFrameworks :=')
            end = value.index('Test / javaOptions +=', start)
            value = value[:start] + value[end:]
            value += SETTINGS
        elif source.name == 'FixtureSuites.scala':
            value = value.replace('    val file = directory.resolve(suite + "-" + index + ".body")', '    val pid = Files.write(directory.resolve(suite + "-" + index + ".pid"), ProcessHandle.current().pid().toString.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)\n    require(Files.isRegularFile(pid), "Physical body PID missing")\n    val file = directory.resolve(suite + "-" + index + ".body")')
        destination.write_text(value)
        inputs.extend(freeze_sources([source], fixture, output / 'originals'))
    (build / 'src/test/scala/izumi/fixtures/host/RegistrationLinkageSuite.scala').write_text(SOURCE)
    project = build / 'project'
    project.mkdir()
    (project / 'build.properties').write_text('sbt.version=2.0.9\n')
    (project / 'plugins.sbt').write_text('addSbtPlugin("io.7mind.izumi" % "sbt-distage-testkit" % "' + args.artifact_version + '")\n')
    generated = [dict(path=str(path), sha256=sha(path)) for path in sorted(build.rglob('*')) if path.is_file()]
    (output / 'inputs.json').write_text(json.dumps(dict(originals=inputs, generated=generated), indent=2) + '\n')
    commands = ['set Global / localCacheDirectory := file("' + str(output / 'local-cache') + '")']
    for case in ['fault', 'recovery', 'repeat-fault']:
        commands.extend(['prepareFixture ' + case, 'registrationFailure' if case != 'recovery' else 'testOnly ' + HEALTHY, 'captureRegistrationFixture ' + case])
    commands.extend(['show Test / dependencyClasspath', 'show Test / fullClasspath'])
    argv = ['direnv', 'exec', str(ROOT), 'sh', '-c', 'exec sbt --server --sbt-version 2.0.9 -java-home "$JDK21" -batch -J-Xmx6G "$@"', 'registration-linkage-consumer', '-Dizumi.fixture.scala-version=' + args.scala_version, '-Dizumi.fixture.version=' + args.artifact_version, '-Dizumi.fixture.audit-root=' + str(build / 'target/body-audit'), '-Dizumi.fixture.captures=' + str(output / 'cases'), *commands]
    (output / 'commands.json').write_text(json.dumps(dict(cwd=str(build), argv=argv), indent=2) + '\n')
    print(json.dumps(dict(state='started', scala=args.scala_version, log=str(output / 'run.log'))), flush=True)
    with (output / 'run.log').open('x') as log:
        actual_exit = run_process(argv, build, log, TIMEOUT_SECONDS, GRACE_SECONDS)
        log.write('\nEXIT ' + str(actual_exit) + '\n')
    raw = (output / 'run.log').read_text()
    valid = actual_exit == 0 and raw.count('REGISTRATION_CONSUMER_THROW pid=') == 2 and raw.count('REGISTRATION_CONSUMER_TASK_REJECTED ') == 2 and raw.count('REGISTRATION_CONSUMER_CAPTURE_OK case=') == 3
    cases = []
    parents = set()
    children = set()
    for case in ['fault', 'recovery', 'repeat-fault']:
        captured = output / 'cases' / case
        audit = captured / 'body-audit'
        if not audit.is_dir() or not all((audit / name).is_file() for name in ['host.parent-pid', 'host.output']):
            valid = False
            continue
        parent = int((audit / 'host.parent-pid').read_text())
        parents.add(parent)
        rows = (audit / 'host.output').read_text().splitlines()
        bodies = [path.read_text().split('\t') for path in sorted(audit.glob('*.body'))]
        acquired = sorted(path.read_text() for path in audit.glob('*.acquire'))
        released = sorted(path.read_text() for path in audit.glob('*.release'))
        reports = [ElementTree.parse(path) for path in sorted((captured / 'test-reports').glob('*.xml'))]
        reported = [(node.attrib['classname'], node.attrib['name']) for report in reports for node in report.findall('.//testcase')]
        error_cases = sum(len(report.findall('.//testcase/error')) for report in reports)
        if case == 'recovery':
            pids = {int(path.read_text()) for path in audit.glob('*.pid')}
            valid = valid and rows == ['\t'.join([HEALTHY, 'Passed', '3', '0', '0', '0', '0', '0', '0'])] and [(row[0], row[1]) for row in bodies] == [(HEALTHY, str(index)) for index in range(1, 4)] and len(acquired) == 1 and acquired == released and {row[2] for row in bodies} == set(acquired) and len(reports) == 1 and sorted(reported) == [(HEALTHY, 'equal display name should ' + name) for name in ['first', 'second', 'third']] and error_cases == 0
        else:
            pids = {int((audit / 'registration.pid').read_text())}
            expected = ['\t'.join([suite, 'Error', '0', '0', '1', '0', '0', '0', '0']) for suite in sorted([FAULT, HEALTHY])]
            valid = valid and rows == expected and not bodies and not acquired and not released and len(reports) == 2 and sorted(suite for suite, _ in reported) == sorted([FAULT, HEALTHY]) and error_cases == 2 and all('fixture-registration-missing-dependency' in ElementTree.tostring(report.getroot(), encoding='unicode') and 'NoClassDefFoundError' in ElementTree.tostring(report.getroot(), encoding='unicode') for report in reports)
        valid = valid and len(pids) == 1 and parent not in pids and not children.intersection(pids) and all(report.getroot().attrib['failures'] == '0' and report.getroot().attrib['skipped'] == '0' for report in reports)
        children.update(pids)
        cases.append(dict(case=case, bodies=len(bodies), errorCases=error_cases, xmlCases=len(reported), publicOutput=rows, parentPid=parent, childPids=sorted(pids), acquired=acquired, released=released))
    valid = valid and len(cases) == 3 and len(parents) == 1 and len(children) == 3 and not any(value in raw for value in ['REGISTRATION_CONSUMER_FALSE_SUCCESS', 'Exception in thread ', 'java.util.concurrent.RejectedExecutionException:'])
    for row in inputs:
        assert sha(row['path']) == sha(row['frozen']) == row['sha256']
    for row in generated:
        assert sha(row['path']) == row['sha256']
    assert sha(__file__) == sha(output / 'driver.py')
    completion = dict(exit=0 if valid else 1, actualExit=actual_exit, valid=valid, scala=args.scala_version, cases=cases, sourceInputsUnchanged=True, scope='Published SBT2 plugin and target bootstrap, real failed testOnly tasks observed through .result, exact per-suite Output/XML errors with retained linkage cause, no fault bodies/resources, one healthy DI recovery in the same SBT process, fresh target PIDs and receipt cleanup. Other fault classes, cancellation, mixed frameworks and complete runner acceptance remain separate.')
    (output / 'completion.json').write_text(json.dumps(completion, indent=2) + '\n')
    print(json.dumps(completion), flush=True)
    raise SystemExit(completion['exit'])


if __name__ == '__main__':
    main()
