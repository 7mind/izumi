#!/usr/bin/env python3
import argparse
from collections import Counter
import json
from pathlib import Path
from xml.etree import ElementTree


import sys
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from fixture_harness import load_module, run_process, sha

CONTROLS = r'''
val prepareSelection = inputKey[Unit]("Prepare one public selection contract")
val captureSelection = inputKey[Unit]("Capture physical selection outcomes")
val probeSelectionOnly = taskKey[Unit]("Retain the public testOnly result")
val probeSelectionQuick = taskKey[Unit]("Retain the public testQuick result")
val probeSelectionMultiple = taskKey[Unit]("Retain the public multiple-pattern result")
val probeSelectionReplacement = taskKey[Unit]("Retain the public replacement-filter result")
prepareSelection := {
  val parsed = spaceDelimited("case mode").parsed
  require(parsed.size == 2, "Expected selection case and mode")
  IO.write(baseDirectory.value / "selection.mode", parsed(1))
  IO.delete(baseDirectory.value / "selection.calls")
  IO.delete(baseDirectory.value / "selection.result")
}
Test / testSelected / testFilter ~= { inherited => arguments =>
  val root = file(sys.props("izumi.fixture.selection-root"))
  val mode = IO.read(root / "selection.mode")
  IO.append(root / "selection.calls", "selected-factory\n")
  if (mode == "factory-failure") sys.error("CUSTOM_SELECTED_FACTORY_FAILURE")
  val filters = if (mode == "replacement") Seq((name: String) => Seq("SuiteA", "SuiteD", "SuiteE").exists(name.endsWith)) else inherited(arguments)
  filters.map(filter => name => {
    IO.append(root / "selection.calls", "selected-predicate\t" + name + "\n")
    if (mode == "predicate-failure" && name.endsWith("SuiteA")) sys.error("CUSTOM_SELECTED_PREDICATE_FAILURE")
    val included = filter(name)
    included && (!(mode == "selected-wrapper" || mode == "configured") || !name.endsWith("SuiteC"))
  })
}
Test / testQuick / testFilter ~= { inherited => arguments =>
  val root = file(sys.props("izumi.fixture.selection-root"))
  val mode = IO.read(root / "selection.mode")
  IO.append(root / "selection.calls", "quick-factory\n")
  inherited(arguments).map(filter => name => {
    IO.append(root / "selection.calls", "quick-predicate\t" + name + "\n")
    filter(name) && (mode != "quick-wrapper" || !name.endsWith("SuiteD"))
  })
}
Test / testOptions ++= Seq("SuiteA", "SuiteB").zipWithIndex.map { case (rejected, index) => Tests.Filter(name => {
  val root = file(sys.props("izumi.fixture.selection-root"))
  if (IO.read(root / "selection.mode") == "configured") {
    IO.append(root / "selection.calls", "configured-" + index + "\t" + name + "\n")
    !name.endsWith(rejected)
  } else true
}) }
probeSelectionOnly := Def.uncached {
  val result = (Test / testOnly).toTask(" *Suite*").result.value.toEither
  IO.write(baseDirectory.value / "selection.result", result.fold(_.toString, _ => "success"))
}
probeSelectionQuick := Def.uncached {
  val result = (Test / testQuick).toTask(" *Suite*").result.value.toEither
  IO.write(baseDirectory.value / "selection.result", result.fold(_.toString, _ => "success"))
}
probeSelectionMultiple := Def.uncached {
  val result = (Test / testOnly).toTask(" *SuiteA *SuiteD").result.value.toEither
  IO.write(baseDirectory.value / "selection.result", result.fold(_.toString, _ => "success"))
}
probeSelectionReplacement := Def.uncached {
  val result = (Test / testOnly).toTask(" *SuiteC").result.value.toEither
  IO.write(baseDirectory.value / "selection.result", result.fold(_.toString, _ => "success"))
}
captureSelection := Def.uncached {
  val parsed = spaceDelimited("case").parsed
  require(parsed.size == 1, "Expected one selection capture")
  val capture = file(sys.props("izumi.fixture.captures")) / parsed.head
  require(!capture.exists(), "Selection capture must be fresh")
  IO.copyDirectory(file(sys.props("izumi.fixture.audit-root")), capture / "body-audit")
  IO.createDirectory(capture / "test-reports")
  val reports = (Test / target).value / "test-reports"
  if (reports.exists()) IO.copyDirectory(reports, capture / "test-reports")
  IO.copyFile(baseDirectory.value / "selection.result", capture / "result")
  IO.copyFile(baseDirectory.value / "selection.calls", capture / "calls")
  IO.copyFile(baseDirectory.value / "selection.mode", capture / "mode")
  IO.write(capture / "host.pid", ProcessHandle.current().pid().toString)
  val receipts = (Test / target).value / "distage-fork-receipts"
  require(!receipts.exists() || (receipts * "*").get().isEmpty, "Selection command left an active receipt")
  streams.value.log.info("OPAQUE_SELECTION_CAPTURED case=" + parsed.head)
}
'''





def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--repo-root', type=Path, required=True)
    parser.add_argument('--evidence-dir', type=Path, required=True)
    parser.add_argument('--artifact-version', required=True)
    parser.add_argument('--plugin-version', required=True)
    parser.add_argument('--scala-version', choices=['3.9.0', '2.13.18'], required=True)
    args = parser.parse_args()
    root = args.repo_root.resolve()
    out = args.evidence_dir.resolve()
    out.mkdir()
    matrix_path = root / 'test-fixtures/sbt-plugin-consumer/verify-matrix.py'
    matrix = load_module('matrix', matrix_path)
    original = root / 'test-fixtures/host-sharing-consumer'
    paths = [original / 'build.sbt', *sorted((original / 'src').rglob('*.scala'))]
    build = out / 'build'
    for path in paths:
        target = build / path.relative_to(original)
        target.parent.mkdir(parents=True, exist_ok=True)
        text = path.read_text()
        if path.name == 'build.sbt':
            start = text.index('Test / testFrameworks :=')
            end = text.index('Test / javaOptions +=', start)
            text = text[:start] + text[end:] + matrix.CONTROLS + CONTROLS
        target.write_text(text)
    (build / 'project').mkdir()
    (build / 'project/build.properties').write_text('sbt.version=2.0.9\n')
    (build / 'project/plugins.sbt').write_text('addSbtPlugin("io.7mind.izumi" % "sbt-distage-testkit" % ' + json.dumps(args.plugin_version) + ')\n')
    external = out / 'external-input.txt'
    external.write_text('alpha')
    cases = []
    commands = ['set Global / localCacheDirectory := file(' + json.dumps(str(out / 'local-cache')) + ')']
    for fork in [False, True]:
        commands.append('set Test / fork := ' + str(fork).lower())
        for label, mode, probe, suites, failure in [
            ('baseline', 'delegate', 'probeSelectionOnly', 'SuiteA SuiteB SuiteC SuiteD SuiteE ForeignSuite', ''),
            ('opaque-only', 'selected-wrapper', 'probeSelectionOnly', 'SuiteA SuiteB SuiteD SuiteE ForeignSuite', ''),
            ('opaque-quick', 'selected-wrapper', 'probeSelectionQuick', 'SuiteA SuiteB SuiteD SuiteE', ''),
            ('replacement', 'replacement', 'probeSelectionReplacement', 'SuiteA SuiteD SuiteE', ''),
            ('quick-only', 'quick-wrapper', 'probeSelectionOnly', 'SuiteA SuiteB SuiteC SuiteD SuiteE ForeignSuite', ''),
            ('quick-quick', 'quick-wrapper', 'probeSelectionQuick', 'SuiteA SuiteB SuiteC SuiteE', ''),
            ('configured', 'configured', 'probeSelectionOnly', 'SuiteD SuiteE ForeignSuite', ''),
            ('multiple', 'selected-wrapper', 'probeSelectionMultiple', 'SuiteA SuiteD', ''),
            ('factory-failure', 'factory-failure', 'probeSelectionOnly', '', 'CUSTOM_SELECTED_FACTORY_FAILURE'),
            ('predicate-failure', 'predicate-failure', 'probeSelectionOnly', '', 'CUSTOM_SELECTED_PREDICATE_FAILURE'),
            ('recovery-only', 'delegate', 'probeSelectionOnly', 'SuiteA SuiteB SuiteC SuiteD SuiteE ForeignSuite', ''),
            ('recovery-quick', 'delegate', 'probeSelectionQuick', 'SuiteA SuiteB SuiteC SuiteD SuiteE', ''),
        ]:
            name = ('fork-' if fork else 'inprocess-') + label
            commands += ['prepareFixture ' + name, 'prepareSelection ' + name + ' ' + mode, probe, 'captureSelection ' + name]
            cases.append(dict(name=name, mode=mode, probe=probe, suites=suites.split(), failure=failure, fork=fork))
    generated = [dict(path=str(p), sha256=sha(p)) for p in sorted(build.rglob('*')) if p.is_file()]
    inputs = [dict(path=str(p), sha256=sha(p)) for p in [*paths, Path(__file__).resolve(), matrix_path]]
    argv = ['direnv', 'exec', str(root), 'sh', '-c', 'exec sbt --server --sbt-version 2.0.9 -java-home "$JDK21" -batch -J-Xmx6G "$@"', 'opaque-selection',
            '-Dizumi.fixture.scala-version=' + args.scala_version, '-Dizumi.fixture.version=' + args.artifact_version,
            '-Dizumi.fixture.audit-root=' + str(build / 'target/body-audit'), '-Dizumi.fixture.captures=' + str(out / 'cases'),
            '-Dizumi.fixture.external-input=' + str(external), '-Dizumi.fixture.selection-root=' + str(build), *commands]
    (out / 'command.json').write_text(json.dumps(dict(argv=argv, cwd=str(build), inputs=inputs, generated=generated, cases=cases), indent=2) + '\n')
    with (out / 'run.log').open('x') as log:
        code = run_process(argv, build, log, matrix.LANE_TIMEOUT_SECONDS, matrix.SHUTDOWN_GRACE_SECONDS)
    failures = []
    checks = []
    resources = set()
    prefix = 'izumi.fixtures.host.'
    for row in cases:
        try:
            capture = out / 'cases' / row['name']
            result = (capture / 'result').read_text()
            assert (row['failure'] in result) if row['failure'] else result == 'success', result
            assert (capture / 'mode').read_text() == row['mode']
            audit = capture / 'body-audit'
            bodies = [p.read_text().strip().split('\t') for p in audit.glob('*.body')]
            expected = Counter((prefix + label, str(i)) for label in row['suites'] for i in range(1, 4))
            assert Counter((b[0], b[1]) for b in bodies) == expected
            acquired = [p.read_text().strip() for p in audit.glob('*.acquire')]
            released = [p.read_text().strip() for p in audit.glob('*.release')]
            required = int(any(suite in ['SuiteC', 'SuiteD', 'SuiteE'] for suite in row['suites']))
            assert len(acquired) == required and sorted(acquired) == sorted(released) and not resources.intersection(acquired)
            resources.update(acquired)
            assert all(b[2] in acquired if b[0] in [prefix + s for s in ['SuiteC', 'SuiteD', 'SuiteE']] else b[2] == 'plain' for b in bodies)
            nodes = [n for p in (capture / 'test-reports').glob('*.xml') for n in ElementTree.parse(p).findall('.//testcase')]
            assert Counter((n.get('classname'), n.get('name')) for n in nodes) == Counter((prefix + label, 'equal display name should ' + leaf) for label in row['suites'] for leaf in ['first', 'second', 'third'])
            assert all(n.find('error') is None and n.find('failure') is None and n.find('skipped') is None for n in nodes)
            calls = (capture / 'calls').read_text().splitlines()
            assert calls.count('selected-factory') == 1
            assert calls.count('quick-factory') == int(row['probe'] == 'probeSelectionQuick')
            predicates = [c.split('\t')[1] for c in calls if c.startswith('selected-predicate\t')]
            if row['mode'] == 'factory-failure':
                assert not predicates
            elif row['mode'] == 'configured':
                assert Counter(predicates) == Counter(prefix + s for s in ['SuiteC', 'SuiteD', 'SuiteE', 'ForeignSuite'])
                assert Counter(c.split('\t')[1] for c in calls if c.startswith('configured-0\t')) == Counter(prefix + s for s in ['SuiteA', 'SuiteB', 'SuiteC', 'SuiteD', 'SuiteE', 'ForeignSuite'])
                assert Counter(c.split('\t')[1] for c in calls if c.startswith('configured-1\t')) == Counter(prefix + s for s in ['SuiteB', 'SuiteC', 'SuiteD', 'SuiteE', 'ForeignSuite'])
            elif row['mode'] != 'predicate-failure':
                names = ['SuiteA', 'SuiteB', 'SuiteC', 'SuiteD', 'SuiteE'] + ([] if row['probe'] == 'probeSelectionQuick' else ['ForeignSuite'])
                repetitions = 2 if row['probe'] == 'probeSelectionMultiple' else 1
                assert Counter(predicates) == Counter({prefix + s: repetitions for s in names})
            checks.append(dict(name=row['name'], bodies=len(bodies), xmlCases=len(nodes), resourceLifetimes=len(acquired), calls=calls))
        except (AssertionError, OSError, KeyError) as cause:
            failures.append(dict(case=row['name'], reason=repr(cause)))
    changed = [r['path'] for r in inputs + generated if sha(Path(r['path'])) != r['sha256']]
    result = dict(exit=int(bool(code) or bool(failures) or bool(changed)), actualExit=code, checks=checks, failures=failures, inputsChanged=changed)
    (out / 'completion.json').write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps(dict(exit=result['exit'], actualExit=code, checks=len(checks), failures=failures)), flush=True)
    raise SystemExit(result['exit'])


if __name__ == '__main__':
    main()
