#!/usr/bin/env python3
import argparse
from collections import Counter
from concurrent.futures import ThreadPoolExecutor
import json
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET

from verify import prepare, sha


def configure(command, scenario):
    build = Path(command['cwd'])
    platform = command['platform']
    argv = command['argv'][:command['argv'].index(platform + '/testFull')]
    if scenario == 'body':
        source = build / 'shared/BodySuite.scala'
        before = source.read_text()
        after = before.replace('println("TARGET_BODY suite=" + getClass.getName + " test=3 marker=" + index); ()', 'println("TARGET_BODY suite=" + getClass.getName + " test=3 marker=" + index); if (index == 5) throw new IllegalStateException("target-owner-candidate.SuiteE"); ()')
        assert after != before
        source.write_text(after)
        cases = [platform + '/testFull']
    else:
        definition = build / 'build.sbt'
        before = definition.read_text()
        after = before.replace('val common = Seq(', '''val armCandidateLaunchFailure = taskKey[Unit]("Arm malformed target runner arguments")
val disarmCandidateLaunchFailure = taskKey[Unit]("Disarm malformed target runner arguments")
val expectCandidateLaunchFailure = taskKey[Unit]("Require a failed target runner creation command")
val common = Seq(
  armCandidateLaunchFailure := Def.uncached { val _ = System.setProperty("candidate.launch-failure", "true"); println("SDK_LAUNCH_FAILURE_ARMED") },
  disarmCandidateLaunchFailure := Def.uncached { val _ = System.setProperty("candidate.launch-failure", "false"); println("SDK_LAUNCH_FAILURE_DISARMED") },
  expectCandidateLaunchFailure := Def.uncached {
    (Test / testFull).result.value match {
      case Result.Inc(_) => println("SDK_EXPECTED_LAUNCH_FAILURE")
      case Result.Value(_) => throw new IllegalStateException("Malformed target arguments unexpectedly succeeded")
    }
  },
  Test / testOptions := Def.uncached {
    val current = (Test / testOptions).value
    if (sys.props.get("candidate.launch-failure").contains("true")) current :+ Tests.Argument(TestFramework("izumi.distage.testkit.runner.bootstrap.Framework"), "--unrecognised-fixture-option")
    else current
  },''')
        assert after != before
        definition.write_text(after)
        cases = [platform + '/testFull', platform + '/collectCandidate normal', platform + '/armCandidateLaunchFailure',
                 platform + '/expectCandidateLaunchFailure', platform + '/collectCandidate launch-failure',
                 platform + '/disarmCandidateLaunchFailure', platform + '/testFull', platform + '/collectCandidate recovery']
    command['argv'] = argv + cases


def verify(command, out, scenario):
    build = Path(command['cwd'])
    log = (out / (command['scala'] + '-' + command['platform'] + '.log')).read_text()
    repetitions = 1 if scenario == 'body' else 2
    assert 'Incomplete runs:' not in log and 'RunTerminatedException' not in log and 'RPCCore$ClosedException' not in log
    expected = Counter((f'candidate.Suite{letter}', str(index), str(marker)) for _ in range(repetitions)
                       for marker, letter in enumerate('ABCDE', 1) for index in [1, 2, 3])
    assert Counter(re.findall(r'TARGET_BODY suite=(\S+) test=(\d+) marker=(\d+)', log)) == expected
    assert log.count('TARGET_HELD_ACQUIRE') == log.count('TARGET_HELD_RELEASE') == 5 * repetitions
    streams = []
    for path in (build / 'frames').glob('*.jsonl'):
        rows = [json.loads(line) for line in path.read_text().splitlines()]
        assert len(rows) == 33 and all(row['schemaVersion'] == 4 for row in rows)
        messages = [row['message'] for row in rows]
        assert [int(row['sequence']) for row in messages[:-1]] == list(range(32))
        assert messages[-1]['kind'] == 'completed'
        outcome = messages[-1]['outcome']
        assert messages[-2]['event']['outcome'] == outcome
        assert len(outcome['results']) == 15 and not outcome['cancelled'] and not outcome['failures']
        key = lambda value: json.dumps(value, sort_keys=True)
        assert Counter(key(result['id']) for result in outcome['results']) == Counter(key(message['event']['test']) for message in messages[:-1] if message['event']['kind'] == 'testStarted')
        assert Counter(key(result['id']) for result in outcome['results']) == Counter(key(message['event']['result']['id']) for message in messages[:-1] if message['event']['kind'] == 'testCompleted')
        assert {result['id']['suite'] for result in outcome['results']} == {'logical:candidate.Suite' + letter for letter in 'ABCDE'}
        failed = [result for result in outcome['results'] if result['status'] == 'failed']
        if scenario == 'body':
            assert len(failed) == 1 and failed[0]['id']['suite'] == 'logical:candidate.SuiteE'
            assert failed[0]['id']['path'][-1] == 'third' and failed[0]['failure']['message'] == 'target-owner-candidate.SuiteE'
        else:
            assert not failed and all(result['status'] == 'succeeded' for result in outcome['results'])
        streams.append(dict(run=outcome['run'], sha256=sha(path)))
    assert len(streams) == repetitions and len({row['run'] for row in streams}) == repetitions
    reports = []
    if scenario == 'body':
        assert 'Failed 1, Errors 0, Passed 14' in log and 'TestsFailedException' in log
        files = list(build.glob('**/explicit/test-reports/*.xml'))
        assert len(files) == 5
        failures = []
        for path in files:
            suite = ET.parse(path).getroot()
            assert len(suite.findall('./testcase')) == 3 and not suite.findall('.//error')
            for case in suite.findall('./testcase'):
                for error in case.findall('./failure'):
                    failures.append((suite.attrib['name'], case.attrib['name'], error.attrib))
            reports.append(dict(path=str(path), sha256=sha(path)))
        assert len(failures) == 1
        suite, case, error = failures[0]
        assert suite == 'candidate.SuiteE' and case == 'equal display name should third'
        assert 'target-owner-candidate.SuiteE' in error['message']
    else:
        for marker in ['SDK_LAUNCH_FAILURE_ARMED', 'SDK_LAUNCH_FAILURE_DISARMED', 'SDK_EXPECTED_LAUNCH_FAILURE']:
            assert log.splitlines().count(marker) == 1
        for label in ['normal', 'launch-failure', 'recovery']:
            platform = 'sjs1' if command['platform'] == 'js' else 'native0.5'
            files = list((build / 'captures' / platform / label).glob('*.xml'))
            assert len(files) == 5
            for path in files:
                suite = ET.parse(path).getroot()
                cases = suite.findall('./testcase')
                assert not suite.findall('.//failure')
                errors = suite.findall('.//error')
                if label == 'launch-failure':
                    assert len(cases) == len(errors) == 1
                    assert 'Each request option requires one value' in errors[0].attrib['message']
                    assert errors[0].attrib['type'].endswith('RPCCore$RPCException')
                else:
                    assert len(cases) == 3 and not errors
                reports.append(dict(path=str(path), sha256=sha(path)))
    return dict(scala=command['scala'], platform=command['platform'], physicalBodies=15 * repetitions, streams=streams, reports=reports)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--repo-root', type=Path, required=True)
    parser.add_argument('--evidence-dir', type=Path, required=True)
    parser.add_argument('--artifact-version', required=True)
    parser.add_argument('--production-host-version', required=True)
    parser.add_argument('--host-threads', choices=['1', '2'], required=True)
    parser.add_argument('--scala-version', nargs='+', choices=['3.9.0', '2.13.18'], required=True)
    parser.add_argument('--logical-suite-alias', action='store_true', required=True)
    parser.add_argument('--scenario', choices=['body', 'launch'], required=True)
    args = parser.parse_args()
    root, out = args.repo_root.resolve(), args.evidence_dir.resolve()
    out.mkdir()
    fixture = root / 'test-fixtures/target-runner-consumer'
    paths = [path for path in fixture.rglob('*') if path.is_file() and path.suffix in ['.scala', '.sbt', '.properties', '.py']]
    inputs = [dict(path=str(path), sha256=sha(path)) for path in paths]
    commands = []
    for compiler in args.scala_version:
        for platform in ['js', 'native']:
            command, prepared = prepare(args, compiler, platform, paths)
            configure(command, args.scenario)
            build = Path(command['cwd'])
            inputs.extend(row for row in prepared if not Path(row['path']).is_relative_to(build))
            inputs.extend(dict(path=str(path), sha256=sha(path)) for path in build.rglob('*') if path.is_file())
            commands.append(command)
    (out / 'commands.json').write_text(json.dumps(dict(inputs=inputs, commands=commands, scenario=args.scenario), indent=2) + '\n')
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
    expected_exit = 1 if args.scenario == 'body' else 0
    assert not changed and all(row['actualExit'] == expected_exit for row in results), results
    lanes = [verify(command, out, args.scenario) for command in commands]
    assert len({stream['run'] for lane in lanes for stream in lane['streams']}) == sum(len(lane['streams']) for lane in lanes)
    report = dict(scenario=args.scenario, hostThreads=int(args.host_threads), lanes=lanes, physicalBodies=sum(lane['physicalBodies'] for lane in lanes))
    (out / 'audit.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(dict(scenario=args.scenario, lanes=len(lanes), physicalBodies=report['physicalBodies'])), flush=True)


if __name__ == '__main__':
    main()
