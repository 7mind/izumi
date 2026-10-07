#!/usr/bin/env python3
from pathlib import Path
from xml.etree import ElementTree
import argparse
import json
import os
import shutil
import signal
import subprocess
import time

import sys
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from fixture_harness import freeze_driver, load_module, sha

ROOT = Path(__file__).resolve().parents[2]
TIMEOUT_SECONDS = 240
GRACE_SECONDS = 10
HOLD_WINDOW_SECONDS = 5
SELECTED = 'fixture.SuiteA fixture.SuiteB'
EXTRA = r'''
lazy val deliveryConsumer = project.in(file(".")).enablePlugins(izumi.distage.sbt.DistageTestkitPlugin)
libraryDependencies += "io.7mind.izumi" %% "distage-test-runner" % sys.props("fixture.artifact-version") % Test
val prepareDelivery = inputKey[Unit]("Prepare one explicit host-delivery control")
prepareDelivery := {
  val parsed = sbt.complete.DefaultParsers.spaceDelimited("mode").parsed
  require(parsed.size == 1 && Set("normal", "held", "recovery").contains(parsed.head), "Delivery mode missing")
  val audit = file(sys.props("fixture.audit-root"))
  require(audit.getCanonicalFile == (baseDirectory.value / "audit").getCanonicalFile, "Unowned delivery audit")
  IO.delete(audit)
  IO.createDirectory(audit)
  IO.delete((Test / target).value / "test-reports")
  IO.write(audit / "mode", parsed.head)
}
val rejectIncompleteDelivery = taskKey[Unit]("Observe the production guard at the public testOnly boundary")
rejectIncompleteDelivery := Def.uncached {
  val audit = file(sys.props("fixture.audit-root"))
  (Test / testOnly).toTask(" fixture.SuiteA fixture.SuiteB").result.value.toEither match {
    case Left(cause) =>
      require(cause.toString.contains("Incomplete distage host result"), "Unexpected delivery rejection: " + cause)
      IO.write(audit / "host.rejected", cause.toString)
      require((audit / "host.returned").isFile, "Held callback did not return")
      streams.value.log.info("PLUGIN_DELIVERY_REJECTED " + cause)
    case Right(_) => sys.error("PLUGIN_DELIVERY_FALSE_SUCCESS")
  }
}
val captureDelivery = inputKey[Unit]("Freeze bodies, SDK output, and reports for one control")
captureDelivery := {
  val parsed: Seq[String] = sbt.complete.DefaultParsers.spaceDelimited("case").parsed;
  Def.uncached {
    require(parsed.size == 1, "Delivery capture requires one case")
    val destination = file(sys.props("fixture.captures")) / parsed.head
    require(!destination.exists(), "Delivery capture must be new")
    IO.copyDirectory(file(sys.props("fixture.audit-root")), destination / "audit")
    IO.copyDirectory((Test / target).value / "test-reports", destination / "test-reports")
    val receipts = (Test / target).value / "distage-fork-receipts"
    require(receipts.isDirectory && (receipts * "*").get().isEmpty, "Delivery receipt survived")
    streams.value.log.info("PLUGIN_DELIVERY_CAPTURE_OK case=" + parsed.head)
  }
}
'''





def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--evidence-dir', type=Path, required=True)
    parser.add_argument('--artifact-version', required=True)
    parser.add_argument('--scala-version', choices=['3.9.0', '2.13.18'], required=True)
    parser.add_argument('--expected-held-result', choices=['reject', 'complete'], required=True)
    args = parser.parse_args()
    out = args.evidence_dir.resolve()
    out.mkdir(exist_ok=False)
    freeze_driver(__file__, out / 'driver.py')
    helper = ROOT / 'test-fixtures/sbt-worker-receipt-race/verify-held-batch.py'
    module = load_module('held_batch', helper)
    shutil.copy2(helper, out / 'source-helper.py')
    build = out / 'build'
    project = build / 'project'
    project.mkdir(parents=True)
    source = build / 'src/test/scala/BatchFramework.scala'
    source.parent.mkdir(parents=True)
    source.write_text(module.SOURCE)
    settings = module.BUILD.replace('SELECTED', 'testSelected')
    settings = settings.replace('scalaVersion := "3.9.0"', 'scalaVersion := sys.props("fixture.scala-version")')
    settings = settings.replace('scalacOptions ++= Seq("-release:17", "-Ybackend-parallelism", "1")', 'scalacOptions ++= { if (scalaVersion.value.startsWith("3.")) Seq("-release:17", "-Ybackend-parallelism", "1") else Seq("-release:17") }')
    settings = settings.replace('sys.props("fixture.mode") == "held"', 'IO.read(audit / "mode") == "held"')
    lines = [line for line in settings.splitlines() if '"GENERIC_HELD_BATCH_OUTPUT_DIFFERS:' not in line]
    (build / 'build.sbt').write_text('\n'.join(lines) + '\n' + EXTRA)
    (project / 'build.properties').write_text('sbt.version=2.0.9\n')
    (project / 'plugins.sbt').write_text('addSbtPlugin("io.7mind.izumi" % "sbt-distage-testkit" % "' + args.artifact_version + '")\n')
    inputs = [dict(path=str(path), sha256=sha(path)) for path in sorted(build.rglob('*')) if path.is_file()]
    commands = ['set Global / localCacheDirectory := file("' + str(out / 'local-cache') + '")']
    for mode in ['normal', 'held', 'recovery']:
        request = 'rejectIncompleteDelivery' if mode == 'held' and args.expected_held_result == 'reject' else 'testOnly ' + SELECTED
        commands += ['prepareDelivery ' + mode, request, 'captureDelivery ' + mode]
    commands += ['show Test / dependencyClasspath']
    argv = ['direnv', 'exec', str(ROOT), 'sh', '-c', 'exec sbt --server --sbt-version 2.0.9 -java-home "$JDK21" -batch -J-Xmx6G "$@"', 'plugin-delivery', '-Dfixture.scala-version=' + args.scala_version, '-Dfixture.artifact-version=' + args.artifact_version, '-Dfixture.audit-root=' + str(build / 'audit'), '-Dfixture.captures=' + str(out / 'cases'), *commands]
    (out / 'commands.json').write_text(json.dumps(dict(cwd=str(build), argv=argv, sources=inputs, helperSha256=sha(helper)), indent=2) + '\n')
    observation = None
    print('PLUGIN_DELIVERY_START scala=' + args.scala_version, flush=True)
    with (out / 'run.log').open('x') as log:
        process = subprocess.Popen(argv, cwd=build, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        deadline = time.monotonic() + TIMEOUT_SECONDS
        audit = build / 'audit'
        while process.poll() is None and time.monotonic() < deadline:
            if observation is None and (audit / 'host.held').is_file() and (audit / 'child.done').is_file():
                time.sleep(HOLD_WINDOW_SECONDS)
                observation = dict(held=(audit / 'host.held').read_text(), returned=(audit / 'host.returned').exists(), rejectionBeforeRelease=(audit / 'host.rejected').read_text() if (audit / 'host.rejected').is_file() else None, outputBeforeRelease=(audit / 'host.output').read_text() if (audit / 'host.output').is_file() else None, bodies=sorted(path.read_text() for path in audit.glob('*.body')))
                (out / 'held-observation.json').write_text(json.dumps(observation, indent=2) + '\n')
                (audit / 'host.allow').write_text('release after frozen held callback observation\n')
            time.sleep(0.02)
        if process.poll() is None:
            os.killpg(process.pid, signal.SIGTERM)
            try:
                process.wait(timeout=GRACE_SECONDS)
            except subprocess.TimeoutExpired:
                os.killpg(process.pid, signal.SIGKILL)
                process.wait()
            actual = 124
        else:
            actual = process.wait()
        log.write('\nEXIT ' + str(actual) + '\n')
    raw = (out / 'run.log').read_text()
    valid = actual == 0 and observation is not None and not observation['returned'] and observation['held'] == 'fixture.SuiteB\t3' and len(observation['bodies']) == 6 and observation['rejectionBeforeRelease'] is None
    expected = [('fixture.Suite' + letter, str(index)) for letter in 'AB' for index in range(1, 4)]
    full_output = '\n'.join('fixture.Suite' + letter + '\tPassed\t3\t0\t0\t0' for letter in 'AB')
    parents = set()
    children = set()
    cases = []
    for mode in ['normal', 'held', 'recovery']:
        capture = out / 'cases' / mode
        audit = capture / 'audit'
        if not audit.is_dir() or not all((audit / name).is_file() for name in ['host.parent', 'child.done', 'host.output']):
            valid = False
            continue
        physical = [path.read_text().split('\t') for path in sorted(audit.glob('*.body'))]
        parent = (audit / 'host.parent').read_text()
        child = (audit / 'child.done').read_text()
        output = (audit / 'host.output').read_text()
        reports = [ElementTree.parse(path) for path in (capture / 'test-reports').glob('*.xml')]
        reported = [(node.attrib['classname'], node.attrib['name']) for report in reports for node in report.findall('.//testcase')]
        valid = valid and sorted((row[0], row[1]) for row in physical) == expected and {row[2] for row in physical} == {child} and child != parent and child not in children and len(reports) == 2 and sorted(reported) == [(suite, 'body-' + index) for suite, index in expected] and all(report.getroot().attrib['tests'] == '3' and all(report.getroot().attrib[key] == '0' for key in ['errors', 'failures', 'skipped']) for report in reports)
        rejected = mode == 'held' and args.expected_held_result == 'reject'
        valid = valid and (output == 'fixture.SuiteA\tPassed\t3\t0\t0\t0' and (audit / 'host.rejected').is_file() and (audit / 'host.returned').is_file() if rejected else output == full_output and not (audit / 'host.rejected').exists())
        parents.add(parent)
        children.add(child)
        cases.append(dict(mode=mode, physicalBodies=len(physical), xmlCases=len(reported), publicOutput=output, taskRejected=rejected, parentPid=parent, childPid=child))
    expected_rejections = 1 if args.expected_held_result == 'reject' else 0
    valid = valid and len(cases) == 3 and len(parents) == 1 and len(children) == 3 and raw.count('PLUGIN_DELIVERY_CAPTURE_OK case=') == 3 and raw.count('PLUGIN_DELIVERY_REJECTED ') == expected_rejections and 'PLUGIN_DELIVERY_FALSE_SUCCESS' not in raw
    for row in inputs:
        assert sha(row['path']) == row['sha256']
    assert sha(helper) == sha(out / 'source-helper.py') and sha(__file__) == sha(out / 'driver.py')
    completion = dict(exit=0 if valid else 1, actualExit=actual, valid=valid, scala=args.scala_version, expectedHeldResult=args.expected_held_result, cases=cases, scope='Published plugin and unchanged generic foreign framework: normal, held and same-session recovery with fresh workers and exact body/XML/output checks. Complete mode requires the held command to succeed with all six cases. Reject mode records the earlier mitigation and cannot establish restoration or final acceptance.')
    (out / 'completion.json').write_text(json.dumps(completion, indent=2) + '\n')
    print(json.dumps(completion), flush=True)
    raise SystemExit(completion['exit'])


if __name__ == '__main__':
    main()
