from pathlib import Path
from xml.etree import ElementTree
import argparse
import importlib.util
import json
import shutil

import sys
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from fixture_harness import freeze_driver, run_process, sha

TIMEOUT_SECONDS = 240
TESTS_PER_SUITE = 3
MODES = ['single', 'serial', 'overlap-serial', 'overlap-parallel', 'empty', 'recovery']
BUILD = r'''
lazy val groupsConsumer = project.in(file(".")).enablePlugins(izumi.distage.sbt.DistageTestkitPlugin)
scalaVersion := sys.props("fixture.scala-version")
libraryDependencies += "io.7mind.izumi" %% "distage-test-runner" % sys.props("fixture.artifact-version") % Test
scalacOptions ++= { if (scalaVersion.value.startsWith("3.")) Seq("-release:17", "-Ybackend-parallelism", "1") else Seq("-release:17") }
Test / fork := false
Test / parallelExecution := false
Test / testFrameworks := Seq(new TestFramework("fixture.GroupFramework"))
Test / javaOptions += "-Dfixture.audit-root=" + sys.props("fixture.audit-root")
Test / testOptions := Def.uncached {
  val inherited = (Test / testOptions).value
  if (IO.read(file(sys.props("fixture.audit-root")) / "mode") == "empty") inherited :+ Tests.Exclude(Seq("fixture.SuiteA", "fixture.SuiteB")) else inherited
}
Test / testGrouping := Def.uncached {
  val mode = IO.read(file(sys.props("fixture.audit-root")) / "mode")
  val definitions = (Test / definedTests).value.sortBy(_.name)
  def group(name: String, tests: Seq[TestDefinition]) = new Tests.Group(name, tests, Tests.InProcess)
  mode match {
    case "single" | "recovery" | "empty" => Seq(group("single", definitions))
    case "serial" => definitions.map(test => group(test.name, Seq(test)))
    case "overlap-serial" | "overlap-parallel" => Seq(group("first", definitions.filter(_.name == "fixture.SuiteA")), group("second", definitions.filter(_.name == "fixture.SuiteA")))
    case other => sys.error("Unknown group mode: " + other)
  }
}
Test / testSelected / testResultLogger := {
  val inherited = (Test / testSelected / testResultLogger).value
  new TestResultLogger {
    override def run(log: sbt.util.Logger, output: Tests.Output, taskName: String): Unit = {
      val audit = file(sys.props("fixture.audit-root"))
      val rows = output.events.toVector.sortBy(_._1).map { case (name, result) => Vector(name, result.result.toString, result.passedCount, result.failureCount, result.errorCount, result.skippedCount).mkString("\t") }
      IO.write(audit / "host.output", rows.mkString("\n"))
      IO.write(audit / "host.parent", ProcessHandle.current().pid().toString)
      inherited.run(log, output, taskName)
    }
  }
}
val prepareGroups = inputKey[Unit]("Prepare an isolated in-process grouping control")
prepareGroups := {
  val parsed = sbt.complete.DefaultParsers.spaceDelimited("mode").parsed
  require(parsed.size == 1, "Missing group mode")
  val audit = file(sys.props("fixture.audit-root"))
  IO.delete(audit); IO.createDirectory(audit)
  IO.delete((Test / target).value / "test-reports")
  IO.write(audit / "mode", parsed.head)
}
val captureGroups = inputKey[Unit]("Freeze body receipts and public reports")
captureGroups := {
  val parsed = sbt.complete.DefaultParsers.spaceDelimited("case").parsed
  Def.uncached {
    require(parsed.size == 1, "Missing capture case")
    val audit = file(sys.props("fixture.audit-root"))
    val destination = file(sys.props("fixture.captures")) / parsed.head
    require(!destination.exists(), "Capture must be new")
    IO.copyDirectory(audit, destination / "audit")
    IO.copyDirectory((Test / target).value / "test-reports", destination / "test-reports")
    streams.value.log.info("INPROCESS_COMMAND_GROUP_CAPTURE_OK case=" + parsed.head)
  }
}
'''





def source_from_fork_fixture():
    path = Path(__file__).with_name('verify-command-groups.py')
    spec = importlib.util.spec_from_file_location('fork_command_groups', path)
    assert spec is not None and spec.loader is not None
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    before = 'definition.fullyQualifiedName() + "-" + index + "-" + pid + ".body"'
    after = 'definition.fullyQualifiedName() + "-" + index + "-" + java.util.UUID.randomUUID().toString + ".body"'
    assert module.SOURCE.count(before) == 1
    return module.SOURCE.replace(before, after), path


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--repo-root', type=Path, required=True)
    parser.add_argument('--evidence-dir', type=Path, required=True)
    parser.add_argument('--artifact-version', required=True)
    parser.add_argument('--scala-version', choices=['3.9.0', '2.13.18'], required=True)
    parser.add_argument('--report-format', choices=['standard', 'legacy'], required=True)
    args = parser.parse_args()
    out = args.evidence_dir.resolve()
    out.mkdir()
    freeze_driver(__file__, out / 'driver.py')
    source, fork_driver = source_from_fork_fixture()
    shutil.copy2(fork_driver, out / fork_driver.name)
    build = out / 'build'
    (build / 'project').mkdir(parents=True)
    scala_source = build / 'src/test/scala/GroupFramework.scala'
    scala_source.parent.mkdir(parents=True)
    scala_source.write_text(source)
    (build / 'build.sbt').write_text(BUILD)
    (build / 'project/build.properties').write_text('sbt.version=2.0.9\n')
    (build / 'project/plugins.sbt').write_text('addSbtPlugin("io.7mind.izumi" % "sbt-distage-testkit" % "' + args.artifact_version + '")\n')
    inputs = [dict(path=str(path), sha256=sha(path)) for path in sorted(build.rglob('*')) if path.is_file()]
    inputs += [dict(path=str(path), sha256=sha(path)) for path in [Path(__file__), fork_driver]]
    commands = ['set Global / localCacheDirectory := file("' + str(out / 'local-cache') + '")']
    parallel = False
    for mode in MODES:
        commands.append('prepareGroups ' + mode)
        requested_parallel = mode == 'overlap-parallel'
        if requested_parallel != parallel:
            commands.append('set Test / parallelExecution := ' + str(requested_parallel).lower())
            parallel = requested_parallel
        commands += ['testOnly fixture.SuiteA fixture.SuiteB', 'captureGroups ' + mode]
    commands += ['show Test / dependencyClasspath']
    argv = ['direnv', 'exec', str(args.repo_root.resolve()), 'sh', '-c', 'exec sbt --server --sbt-version 2.0.9 -java-home "$JDK21" -batch -J-Xmx6G "$@"', 'inprocess-command-groups', '-Dfixture.scala-version=' + args.scala_version, '-Dfixture.artifact-version=' + args.artifact_version, '-Dfixture.audit-root=' + str(build / 'audit'), '-Dfixture.captures=' + str(out / 'cases'), *commands]
    (out / 'commands.json').write_text(json.dumps(dict(cwd=str(build), argv=argv, inputs=inputs), indent=2) + '\n')
    with (out / 'run.log').open('x') as log:
        actual = run_process(argv, build, log, TIMEOUT_SECONDS, 10)
        log.write('\nEXIT ' + str(actual) + '\n')
    failures = []
    cases = []
    parents = set()
    if actual != 0:
        failures.append('SBT failed: inspect run.log')
    else:
        for mode in MODES:
            capture = out / 'cases' / mode
            bodies = [path.read_text().split('\t') for path in (capture / 'audit').glob('*.body')]
            expected = {'fixture.SuiteA': 2 * TESTS_PER_SUITE} if mode.startswith('overlap-') else {} if mode == 'empty' else {'fixture.SuiteA': TESTS_PER_SUITE, 'fixture.SuiteB': TESTS_PER_SUITE}
            identities = sorted((row[0], row[1]) for row in bodies)
            wanted = sorted((name, 'body-' + str(index)) for name, count in expected.items() for index in range(1, TESTS_PER_SUITE + 1) for _ in range(count // TESTS_PER_SUITE))
            if identities != sorted((name, selector.removeprefix('body-')) for name, selector in wanted):
                failures.append('Body identities differ: ' + mode)
            parent = (capture / 'audit/host.parent').read_text()
            parents.add(parent)
            if any(row[2] != parent for row in bodies):
                failures.append('Body executed outside SBT process: ' + mode)
            output = (capture / 'audit/host.output').read_text()
            expected_output = '\n'.join(name + '\tPassed\t' + str(count) + '\t0\t0\t0' for name, count in sorted(expected.items()))
            if output != expected_output:
                failures.append('Public Output differs: ' + mode)
            files = list((capture / 'test-reports').glob('*.xml'))
            reports = [ElementTree.parse(path) for path in files]
            xml_cases = [node for report in reports for node in report.findall('.//testcase')]
            if sorted((node.attrib['classname'], node.attrib['name']) for node in xml_cases) != wanted:
                failures.append('XML identities differ: ' + mode)
            if len(files) != len(expected):
                failures.append('XML suite file count differs: ' + mode)
            prefix = 'TEST-' if args.report_format == 'standard' else ''
            if sorted(path.name for path in files) != sorted(prefix + name + '.xml' for name in expected):
                failures.append('XML filenames differ: ' + mode)
            for report in reports:
                suite = report.getroot()
                if int(suite.attrib['tests']) != expected[suite.attrib['name']]:
                    failures.append('XML suite count differs: ' + mode)
            cases.append(dict(mode=mode, bodies=len(bodies), xmlCases=len(xml_cases), output=output, parent=parent))
        if len(parents) != 1:
            failures.append('Host session changed')
    for row in inputs:
        assert sha(Path(row['path'])) == row['sha256']
    result = dict(exit=int(bool(failures)), actualExit=actual, scala=args.scala_version, reportFormat=args.report_format, cases=cases, failures=failures, scope='In-process single, serial, duplicate serial/parallel groups, exclusions and same-session recovery; exact body identities, outer custom Output and XML cases.')
    (out / 'completion.json').write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps(result), flush=True)
    raise SystemExit(result['exit'])


if __name__ == '__main__':
    main()
