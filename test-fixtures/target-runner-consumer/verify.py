#!/usr/bin/env python3
import argparse
from collections import Counter
from concurrent.futures import ThreadPoolExecutor
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import xml.etree.ElementTree as ET


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def verify(command, out):
    compiler, platform = command['scala'], command['platform']
    build = Path(command['cwd'])
    log = (out / (compiler + '-' + platform + '.log')).read_text()
    lines = log.splitlines()
    for marker in ['SDK_INTERRUPTION_ARMED', 'SDK_INTERRUPTION_DISARMED', 'SDK_EXPECTED_CANCELLATION_FAILURE']:
        assert lines.count(marker) == 1, (command, marker)
    assert log.count('SDK_EXECUTION_INTERRUPT') == 1
    if not command['productionHost']:
        assert log.count('SDK_PARENT_INTERRUPTED') == log.count('TARGET_CANCEL_SENT') == 1
    assert 'Incomplete runs:' not in log and 'RunTerminatedException' not in log and 'RPCCore$ClosedException' not in log
    assert 'Errors 5' in log
    physical = re.findall(r'TARGET_BODY suite=(\S+) test=(\d+) marker=(\d+)', log)
    expected = Counter({(f'candidate.Suite{letter}', str(index), str(marker)): 3
                        for marker, letter in enumerate('ABCDE', 1) for index in [1, 2, 3]})
    assert Counter(physical) == expected and len(physical) == 45
    assert log.count('TARGET_HELD_ACQUIRE') == log.count('TARGET_HELD_RELEASE') == 15
    streams = []
    for file in (build / 'frames').glob('*.jsonl'):
        rows = [json.loads(line) for line in file.read_text().splitlines()]
        assert len(rows) == 33 and all(row['schemaVersion'] == 4 for row in rows)
        events = [row['message'] for row in rows if row['message']['kind'] == 'event']
        assert [int(row['sequence']) for row in events] == list(range(32))
        terminal = rows[-1]['message']
        assert terminal['kind'] == 'completed'
        outcome = terminal['outcome']
        assert len(outcome['results']) == 15 and outcome['failures'] == []
        assert events[-1]['event']['outcome'] == outcome
        starts = [row['event']['test'] for row in events if row['event']['kind'] == 'testStarted']
        finishes = [row['event']['result'] for row in events if row['event']['kind'] == 'testCompleted']
        identities = lambda values: Counter(json.dumps(value, sort_keys=True) for value in values)
        assert identities(starts) == identities(value['id'] for value in finishes) == identities(value['id'] for value in outcome['results'])
        assert all(count == 1 for count in identities(starts).values())
        assert all(value['status'] == 'succeeded' for value in outcome['results'])
        streams.append(dict(run=outcome['run'], cancelled=outcome['cancelled'], sha256=sha(file)))
    assert len(streams) == 3 and sum(row['cancelled'] for row in streams) == 1
    assert len({row['run'] for row in streams}) == 3
    xml = []
    for label in ['normal', 'cancelled', 'recovery']:
        files = list((build / 'captures' / ('sjs1' if platform == 'js' else 'native0.5') / label).glob('*.xml'))
        assert len(files) == 5
        seen = set()
        for file in files:
            root = ET.parse(file).getroot()
            assert root.attrib['name'] not in seen
            seen.add(root.attrib['name'])
            entries = root.findall('.//testcase')
            bodies = [case for case in entries if not case.findall('./error') and not case.findall('./failure')]
            assert len(bodies) == 3 and {case.attrib['name'] for case in bodies} == {
                f'equal display name should {name}' for name in ['first', 'second', 'third']}
            errors = [case for case in entries if case.findall('./error') or case.findall('./failure')]
            assert len(errors) == (1 if label == 'cancelled' else 0)
            for case in errors:
                error = case.find('./error')
                assert error is not None and 'InterruptedException' in error.attrib.get('type', '')
                assert 'Target application was cancelled' in error.attrib.get('message', '')
        assert seen == {f'candidate.Suite{letter}' for letter in 'ABCDE'}
        xml.append(dict(case=label, bodyCases=15, errors=5 if label == 'cancelled' else 0))
    return dict(scala=compiler, platform=platform, physicalBodies=45, xml=xml, streams=streams)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--repo-root', type=Path, required=True)
    parser.add_argument('--evidence-dir', type=Path, required=True)
    parser.add_argument('--artifact-version', required=True)
    parser.add_argument('--production-host-version')
    parser.add_argument('--host-threads', choices=['1', '2'], required=True)
    parser.add_argument('--scala-version', nargs='+', choices=['3.9.0', '2.13.18'], required=True)
    args = parser.parse_args()
    root, out = args.repo_root.resolve(), args.evidence_dir.resolve()
    out.mkdir()
    fixture = root / 'test-fixtures/target-runner-consumer'
    paths = [path for path in fixture.rglob('*') if path.is_file() and path.suffix in ['.scala', '.sbt', '.properties']]
    inputs = [dict(path=str(path), sha256=sha(path)) for path in paths]
    commands = []
    for compiler in args.scala_version:
        for platform in ['js', 'native']:
            build = out / (compiler + '-' + platform)
            for path in paths:
                destination = build / path.relative_to(fixture)
                destination.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(path, destination)
            if args.production_host_version:
                shutil.copyfile(build / 'support/ProductionInterruption.scala', build / 'project/ProductionInterruption.scala')
                (build / 'project/TransportProjection.scala').unlink()
                plugins = build / 'project/plugins.sbt'
                plugins.write_text('\n'.join('addSbtPlugin("io.7mind.izumi" % "sbt-distage-testkit-' + target + '" % "' + args.production_host_version + '")' for target in ['js', 'native']) + '\n')
                definition = build / 'build.sbt'
                text = definition.read_text().replace('TransportJsProjectionPlugin', 'ProductionJsInterruptionPlugin').replace('TransportNativeProjectionPlugin', 'ProductionNativeInterruptionPlugin')
                text = '\n'.join(line for line in text.splitlines() if not line.strip().startswith('Test / testOptions += Tests.Argument(')) + '\n'
                text = 'import izumi.distage.sbt.DistageTestkitPlugin.autoImport.*\n' + text
                text = text.replace('val common = Seq(', 'val common = Seq(\n  Test / distageBuildId := "candidate",\n  Test / distageTargetId := "candidate-" + candidatePlatform.value,\n  Test / distageCatalogueId := "candidate",\n  Test / distageEventDirectory := file(sys.props("candidate.frames")),')
                definition.write_text(text)
                for module in ['sbt-distage-testkit', 'sbt-distage-testkit-js', 'sbt-distage-testkit-native']:
                    publication = Path.home() / '.ivy2/local/io.7mind.izumi' / (module + '_sbt2_3') / args.production_host_version
                    assert publication.is_dir(), publication
                    inputs.extend(dict(path=str(path), sha256=sha(path)) for path in publication.rglob('*') if path.is_file())
            inputs.extend(dict(path=str(path), sha256=sha(path)) for path in build.rglob('*') if path.is_file())
            name = 'distage-test-runner_' + ('sjs1' if platform == 'js' else 'native0.5') + '_' + ('3' if compiler.startswith('3.') else '2.13')
            publication = Path.home() / '.ivy2/local/io.7mind.izumi' / name / args.artifact_version
            assert publication.is_dir(), publication
            inputs.extend(dict(path=str(path), sha256=sha(path)) for path in publication.rglob('*') if path.is_file())
            cases = [platform + '/testFull', platform + '/collectCandidate normal', platform + '/armCandidateInterruption',
                     platform + '/expectCandidateCancellation', platform + '/collectCandidate cancelled',
                     platform + '/disarmCandidateInterruption', platform + '/testFull', platform + '/collectCandidate recovery']
            argv = ['direnv', 'exec', str(root), 'sh', '-c',
                    'exec sbt --server --sbt-version 2.0.9 -java-home "$JDK21" -batch -J-Xmx6G "$@"', 'target-runner',
                    '-Dfixture.artifact-version=' + args.artifact_version, '-Dfixture.host-threads=' + args.host_threads,
                    '-Dcandidate.scala=' + compiler, '-Dcandidate.interrupt=false',
                    '-Dcandidate.frames=' + str(build / 'frames'), '-Dcandidate.captures=' + str(build / 'captures'), *cases]
            commands.append(dict(scala=compiler, platform=platform, cwd=str(build), argv=argv, productionHost=bool(args.production_host_version)))
    (out / 'commands.json').write_text(json.dumps(dict(inputs=inputs, commands=commands), indent=2) + '\n')

    def run(command):
        with (out / (command['scala'] + '-' + command['platform'] + '.log')).open('x') as log:
            result = subprocess.run(command['argv'], cwd=command['cwd'], stdout=log, stderr=subprocess.STDOUT, timeout=1200)
        row = dict(scala=command['scala'], platform=command['platform'], actualExit=result.returncode)
        print(json.dumps(row), flush=True)
        return row

    with ThreadPoolExecutor(max_workers=2) as pool:
        results = list(pool.map(run, commands))
    changed = [row['path'] for row in inputs if sha(Path(row['path'])) != row['sha256']]
    (out / 'completion.json').write_text(json.dumps(dict(lanes=results, inputsChanged=changed), indent=2) + '\n')
    assert not changed and all(row['actualExit'] == 0 for row in results), results
    lanes = [verify(command, out) for command in commands]
    assert len({stream['run'] for lane in lanes for stream in lane['streams']}) == 3 * len(lanes)
    report = dict(hostThreads=int(args.host_threads), commands=3 * len(lanes), physicalBodies=45 * len(lanes),
                  xmlBodyCases=45 * len(lanes), suiteCancellationErrors=5 * len(lanes), lanes=lanes)
    (out / 'audit.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps({key: value for key, value in report.items() if key != 'lanes'}), flush=True)


if __name__ == '__main__':
    main()
