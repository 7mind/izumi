#!/usr/bin/env python3
import argparse
import json
import os
from pathlib import Path
import subprocess
from xml.etree import ElementTree


import sys
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from fixture_harness import load_module, sha, wait_process

CONTROLS = r'''
val fixtureRuntimeProperty = settingKey[String]("Untracked plugin input")
val verifyRuntimeInputs = inputKey[Unit]("Verify actual plugin property/environment inputs")
val captureRuntimeClassDirectory = taskKey[Unit]("Record the public target class directory")
fixtureRuntimeProperty := "one"
captureRuntimeClassDirectory := {
  IO.write(baseDirectory.value / "class.directory", (Test / classDirectory).value.toPath.toAbsolutePath.normalize().toString)
}
Test / javaOptions += "-Dizumi.fixture.property-revision=" + fixtureRuntimeProperty.value
Test / testOptions += {
  val revision = fixtureRuntimeProperty.value
  Tests.Setup(() => { val _ = System.setProperty("izumi.fixture.property-revision", revision) })
}
Test / testOptions += Tests.Cleanup(() => { val _ = System.clearProperty("izumi.fixture.property-revision") })
verifyRuntimeInputs := {
  val parsed = spaceDelimited("case property environment").parsed
  require(parsed.size == 3, "Expected runtime input case and revisions")
  val capture = file(sys.props("izumi.fixture.captures")) / parsed.head / "body-audit"
  val resources = (capture * "*.acquire").get().map(IO.read(_).trim)
  require(resources.size == 1 && resources.head.startsWith("prod-" + parsed(1) + "-" + parsed(2) + "-"), "RUNTIME_PLUGIN_INPUTS_STALE: " + resources)
  IO.write(capture.getParentFile / "runtime.inputs", parsed.drop(1).mkString("\t"))
  streams.value.log.info("RUNTIME_PLUGIN_INPUTS_OK case=" + parsed.head + " property=" + parsed(1) + " environment=" + parsed(2))
}
'''





def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--repo-root', type=Path, required=True)
    parser.add_argument('--evidence-dir', type=Path, required=True)
    parser.add_argument('--artifact-version', required=True)
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
        destination = build / path.relative_to(original)
        destination.parent.mkdir(parents=True, exist_ok=True)
        text = path.read_text()
        if path.name == 'build.sbt':
            start = text.index('Test / testFrameworks :=')
            end = text.index('Test / javaOptions +=', start)
            text = text[:start] + text[end:] + matrix.CONTROLS + matrix.SBT2_DIGEST_CONTROL + CONTROLS
        if path.name == 'FixturePlugin.scala':
            before = 'repo + "-" + UUID.randomUUID().toString'
            after = 'repo + "-" + sys.props("izumi.fixture.property-revision") + "-" + sys.env("IZUMI_FIXTURE_ENV_REVISION") + "-" + UUID.randomUUID().toString'
            assert text.count(before) == 1
            text = text.replace(before, after)
        destination.write_text(text)
    (build / 'project').mkdir()
    (build / 'project/build.properties').write_text('sbt.version=2.0.9\n')
    (build / 'project/plugins.sbt').write_text('addSbtPlugin("io.7mind.izumi" % "sbt-distage-testkit" % ' + json.dumps(args.artifact_version) + ')\n')
    external = out / 'external-input.txt'
    external.write_text('alpha')
    inputs = [dict(path=str(p), sha256=sha(p)) for p in [*paths, Path(__file__).resolve(), matrix_path]]
    generated = [dict(path=str(p), sha256=sha(p)) for p in sorted(build.rglob('*')) if p.is_file()]
    commands = []
    epochs = []
    compiled = None
    for environment in ['alpha', 'beta']:
        capture_root = out / environment
        request = ['set Global / localCacheDirectory := file(' + json.dumps(str(out / 'local-cache')) + ')',
                   'verifyDistinctStockDigests' if environment == 'alpha' else 'verifyScannedImplementationDigests']
        cases = []
        for fork in [False, True]:
            request.append('set Test / fork := ' + str(fork).lower())
            for revision, key in [('one', 'testFull'), ('two', 'test'), ('three', 'testQuick')]:
                name = ('fork-' if fork else 'inprocess-') + revision
                suites = matrix.ALL_SUITES + (' ForeignSuite' if key == 'testFull' else '')
                request += ['set fixtureRuntimeProperty := ' + json.dumps(revision), 'prepareFixture ' + name, key,
                            'verifyFixture ' + name + ' 1 ' + suites,
                            'verifyRuntimeInputs ' + name + ' ' + revision + ' ' + environment,
                            'verifyScannedImplementationDigests']
                cases.append(dict(name=name, revision=revision, environment=environment, request=key, suites=suites.split()))
        request.append('captureRuntimeClassDirectory')
        argv = ['direnv', 'exec', str(root), 'sh', '-c', 'exec sbt --server --sbt-version 2.0.9 -java-home "$JDK21" -batch -J-Xmx6G "$@"', 'runtime-inputs',
                '-Dizumi.fixture.scala-version=' + args.scala_version, '-Dizumi.fixture.version=' + args.artifact_version,
                '-Dizumi.fixture.audit-root=' + str(build / 'target/body-audit'), '-Dizumi.fixture.captures=' + str(capture_root),
                '-Dizumi.fixture.external-input=' + str(external), *request]
        row = dict(environment=environment, argv=argv, cwd=str(build), cases=cases)
        commands.append(row)
        (out / 'command.json').write_text(json.dumps(dict(inputs=inputs, generated=generated, epochs=commands), indent=2) + '\n')
        with (out / (environment + '.log')).open('x') as log:
            process = subprocess.Popen(argv, cwd=build, stdout=log, stderr=subprocess.STDOUT, start_new_session=True,
                                       env=dict(os.environ, IZUMI_FIXTURE_ENV_REVISION=environment))
            code = wait_process(process, matrix.LANE_TIMEOUT_SECONDS, matrix.SHUTDOWN_GRACE_SECONDS)
        classes = Path((build / 'class.directory').read_text())
        assert classes.is_relative_to(build), 'Runtime-input class directory escaped the fixture build'
        current = {str(p.relative_to(classes)): sha(p) for p in classes.rglob('*') if p.is_file() and p.suffix in ['.class', '.tasty']}
        assert current, 'Runtime-input fixture compiled no target payloads'
        unchanged = compiled is None or current == compiled
        if compiled is None:
            compiled = current
        raw = (out / (environment + '.log')).read_text()
        failures = []
        resources = set()
        for case in cases:
            try:
                capture = capture_root / case['name']
                assert (capture / 'runtime.inputs').read_text() == case['revision'] + '\t' + environment
                marker = 'TARGET_BOOTSTRAP_PREPARED case=' + case['name'] + '\n'
                start = raw.index(marker) + len(marker)
                end = raw.find('TARGET_BOOTSTRAP_PREPARED case=', start)
                segment = raw[start:end if end >= 0 else len(raw)]
                if case['request'] != 'testFull':
                    for suite in case['suites']:
                        assert 'DISTAGE_CACHE_DECISION suite=izumi.fixtures.host.' + suite + ' decision=rerun reason=untracked-input-closure' in segment
                audit = capture / 'body-audit'
                bodies = [p.read_text().strip().split('\t') for p in audit.glob('*.body')]
                expected = sorted(('izumi.fixtures.host.' + s, str(i)) for s in case['suites'] for i in range(1, 4))
                assert sorted((b[0], b[1]) for b in bodies) == expected
                acquired = [p.read_text().strip() for p in audit.glob('*.acquire')]
                released = [p.read_text().strip() for p in audit.glob('*.release')]
                assert len(acquired) == 1 and acquired == released and not resources.intersection(acquired)
                resources.update(acquired)
                assert acquired[0].startswith('prod-' + case['revision'] + '-' + environment + '-')
                nodes = [n for p in (capture / 'test-reports').glob('*.xml') for n in ElementTree.parse(p).findall('.//testcase')]
                assert len(nodes) == len(expected)
                assert all(n.find('error') is None and n.find('failure') is None for n in nodes)
            except (AssertionError, ValueError, OSError) as cause:
                failures.append(dict(case=case['name'], reason=repr(cause)))
        assert raw.count('PLUGIN_SCANNED_DIGESTS_UNCHANGED_OK') == (6 if environment == 'alpha' else 7) or code != 0
        epochs.append(dict(environment=environment, actualExit=code, failures=failures, compiledUnchanged=unchanged, cases=cases))
        if code or failures or not unchanged:
            break
    changed = [r['path'] for r in inputs + generated if sha(Path(r['path'])) != r['sha256']]
    result = dict(exit=int(len(epochs) != 2 or bool(changed) or any(r['actualExit'] or r['failures'] or not r['compiledUnchanged'] for r in epochs)), epochs=epochs, inputsChanged=changed, compiledPayloads=compiled)
    (out / 'completion.json').write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps(dict(exit=result['exit'], epochs=[{k: v for k, v in r.items() if k != 'cases'} for r in epochs])), flush=True)
    raise SystemExit(result['exit'])


if __name__ == '__main__':
    main()
