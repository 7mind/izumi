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


CONTROLS = '''
val policyOptions = settingKey[Seq[TestOption]]("Explicit host selection policy")
val preparePolicy = inputKey[Unit]("Begin a fresh policy command capture")
val collectPolicy = inputKey[Unit]("Capture policy XML and SDK frames")
'''
SETTINGS = '''
  policyOptions := Seq.empty,
  Test / testOptions ++= policyOptions.value,
  Test / testOptions ++= Seq(
    Tests.Setup(() => println("SDK_POLICY_SETUP")),
    Tests.Cleanup(() => println("SDK_POLICY_CLEANUP")),
  ),
  preparePolicy := {
    val labels = spaceDelimited("case").parsed
    require(labels.size == 1, "Expected one policy case")
    IO.delete(target.value / "explicit" / "test-reports")
    IO.delete(file(sys.props("candidate.frames")))
    println("SDK_POLICY_BEGIN " + labels.head)
  },
  collectPolicy := {
    val labels = spaceDelimited("case").parsed
    require(labels.size == 1, "Expected one policy case")
    val destination = file(sys.props("candidate.captures")) / labels.head
    require(!destination.exists(), "Policy capture already exists")
    IO.createDirectory(destination)
    IO.copyDirectory(target.value / "explicit" / "test-reports", destination / "xml")
    IO.copyDirectory(file(sys.props("candidate.frames")), destination / "frames")
    println("SDK_POLICY_END " + labels.head)
  },
'''


def cases(platform):
    test = dict(target='candidate-' + ('sjs1' if platform == 'js' else 'native0.5'),
                suite='logical:candidate.SuiteB', path=['equal display name', 'should', 'third'], variant=None)
    encoded = json.dumps(json.dumps(test, separators=(',', ':')))
    all_tests = [(letter, index) for letter in 'ABCDE' for index in [1, 2, 3]]
    subset = lambda letters: [(letter, index) for letter in letters for index in [1, 2, 3]]
    options = lambda expression: ['set ' + platform + '/policyOptions := ' + expression]
    return [
        dict(name='full', request='testFull', tests=all_tests, before=[]),
        dict(name='two-suites', request='testOnly *SuiteB *SuiteD', tests=subset('BD'), before=[]),
        dict(name='wildcard', request='testOnly *SuiteA', tests=subset('A'), before=[]),
        dict(name='individual', request='testOnly *SuiteB -- --test-id ' + encoded, tests=[('B', 3)], before=[]),
        dict(name='after-partial', request='test', tests=all_tests, before=[]),
        dict(name='incremental', request='test', tests=all_tests, before=[]),
        dict(name='quick', request='testQuick', tests=all_tests, before=[]),
        dict(name='quick-selected', request='testQuick *SuiteB *SuiteD', tests=subset('BD'), before=[]),
        dict(name='quick-complete', request='testQuick', tests=all_tests, before=[]),
        dict(name='filter', request='test', tests=subset('ABCD'), before=options('Seq(Tests.Filter(name => name != "candidate.SuiteE"))')),
        dict(name='filter-selected', request='testOnly *SuiteD *SuiteE', tests=subset('D'), before=[]),
        dict(name='exclude', request='testQuick', tests=subset('ACE'), before=options('Seq(Tests.Exclude(Seq("candidate.SuiteB", "candidate.SuiteD")))')),
        dict(name='filter-exclude', request='test', tests=subset('AC'), before=options('Seq(Tests.Filter(name => name != "candidate.SuiteE"), Tests.Exclude(Seq("candidate.SuiteB", "candidate.SuiteD")))')),
        dict(name='empty', request='testOnly *SuiteB *SuiteD *SuiteE', tests=[], before=[]),
        dict(name='reset-complete', request='testQuick', tests=all_tests, before=options('Seq.empty')),
        dict(name='serial', request='testFull', tests=all_tests, before=['set ' + platform + '/Test/parallelExecution := false']),
        dict(name='host-one', request='testFull', tests=all_tests, before=['set ' + platform + '/Test/parallelExecution := true', 'set Global / concurrentRestrictions := Seq(Tags.limit(Tags.Test, 1))']),
        dict(name='repeat', request='testFull', tests=all_tests, before=[]),
    ]


def audit(command, out):
    build = Path(command['cwd'])
    log = (out / (command['scala'] + '-' + command['platform'] + '.log')).read_text()
    result = []
    for case in command['cases']:
        label = case['name']
        begin, end = 'SDK_POLICY_BEGIN ' + label + '\n', 'SDK_POLICY_END ' + label + '\n'
        assert log.count(begin) == log.count(end) == 1, (label, 'capture boundaries')
        segment = log.split(begin, 1)[1].split(end, 1)[0]
        wanted = Counter((f'candidate.Suite{letter}', str(index), str('ABCDE'.index(letter) + 1)) for letter, index in case['tests'])
        assert Counter(re.findall(r'TARGET_BODY suite=(\S+) test=(\d+) marker=(\d+)', segment)) == wanted, (label, 'physical bodies')
        held = sum(index == 3 for _, index in case['tests'])
        assert segment.count('TARGET_HELD_ACQUIRE') == segment.count('TARGET_HELD_RELEASE') == held, (label, 'held callbacks')
        capture = build / 'captures' / label
        xml_cases = Counter()
        for path in (capture / 'xml').glob('*.xml'):
            xml = ET.parse(path).getroot()
            assert not xml.findall('.//failure') and not xml.findall('.//error'), (label, path)
            for item in xml.findall('.//testcase'):
                xml_cases[(xml.attrib['name'], item.attrib['name'])] += 1
        names = {1: 'first', 2: 'second', 3: 'third'}
        expected_xml = Counter((f'candidate.Suite{letter}', 'equal display name should ' + names[index]) for letter, index in case['tests'])
        assert xml_cases == expected_xml, (label, 'XML identities', xml_cases, expected_xml)
        streams = list((capture / 'frames').glob('*.jsonl'))
        assert len(streams) == int(bool(case['tests'])), (label, 'execution process count')
        run = None
        if streams:
            assert segment.count('SDK_POLICY_SETUP') == segment.count('SDK_POLICY_CLEANUP') == 1, (label, 'callbacks')
            assert segment.index('SDK_POLICY_SETUP') < segment.index('TARGET_BODY') < segment.index('SDK_POLICY_CLEANUP'), (label, 'callback ordering')
            frames = [json.loads(line) for line in streams[0].read_text().splitlines()]
            assert all(frame['schemaVersion'] == 4 for frame in frames)
            messages = [frame['message'] for frame in frames]
            terminal = messages[-1]
            assert terminal['kind'] == 'completed'
            outcome = terminal['outcome']
            assert not outcome['cancelled'] and not outcome['failures']
            events = [message for message in messages if message['kind'] == 'event']
            assert [int(event['sequence']) for event in events] == list(range(len(events)))
            starts = [event['event']['test'] for event in events if event['event']['kind'] == 'testStarted']
            finishes = [event['event']['result'] for event in events if event['event']['kind'] == 'testCompleted']
            expected_ids = Counter(('logical:candidate.Suite' + letter, 'equal display name', 'should', names[index]) for letter, index in case['tests'])
            identities = lambda ids: Counter((value['suite'], *value['path']) for value in ids)
            assert identities(starts) == identities(value['id'] for value in finishes) == identities(value['id'] for value in outcome['results']) == expected_ids
            assert all(value['status'] == 'succeeded' for value in outcome['results'])
            assert events[-1]['event']['outcome'] == outcome
            run = outcome['run']
        result.append(dict(case=label, tests=len(case['tests']), run=run,
                           captures=[dict(path=str(path), sha256=sha(path)) for path in capture.rglob('*') if path.is_file()]))
    assert 'Incomplete runs:' not in log and 'RunTerminatedException' not in log and 'RPCCore$ClosedException' not in log
    return dict(scala=command['scala'], platform=command['platform'], cases=result)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--repo-root', type=Path, required=True)
    parser.add_argument('--evidence-dir', type=Path, required=True)
    parser.add_argument('--artifact-version', required=True)
    parser.add_argument('--production-host-version', required=True)
    parser.add_argument('--scala-version', nargs='+', choices=['3.9.0', '2.13.18'], required=True)
    args = parser.parse_args()
    args.host_threads, args.logical_suite_alias = '2', True
    root, out = args.repo_root.resolve(), args.evidence_dir.resolve()
    out.mkdir()
    fixture = root / 'test-fixtures/target-runner-consumer'
    paths = [path for path in fixture.rglob('*') if path.is_file() and path.suffix in ['.scala', '.sbt', '.properties', '.py']]
    inputs = [dict(path=str(path), sha256=sha(path)) for path in paths]
    commands = []
    for compiler in args.scala_version:
        for platform in ['js', 'native']:
            command, prepared = prepare(args, compiler, platform, paths)
            build = Path(command['cwd'])
            (build / 'project/ProductionInterruption.scala').unlink()
            definition = build / 'build.sbt'
            text = definition.read_text().replace('ProductionJsInterruptionPlugin', 'DistageTestkitJsPlugin').replace('ProductionNativeInterruptionPlugin', 'DistageTestkitNativePlugin')
            assert text.count('val common = Seq(') == 1
            definition.write_text(text.replace('val common = Seq(', CONTROLS + '\nval common = Seq(\n' + SETTINGS))
            command['cases'] = cases(platform)
            requests = []
            for case in command['cases']:
                requests += case['before'] + [platform + '/preparePolicy ' + case['name'], platform + '/' + case['request'], platform + '/collectPolicy ' + case['name']]
            command['argv'] = command['argv'][:command['argv'].index(platform + '/testFull')] + requests
            inputs.extend(row for row in prepared if not Path(row['path']).is_relative_to(build))
            inputs.extend(dict(path=str(path), sha256=sha(path)) for path in build.rglob('*') if path.is_file())
            commands.append(command)
    (out / 'commands.json').write_text(json.dumps(dict(inputs=inputs, commands=commands), indent=2) + '\n')

    def run(command):
        with (out / (command['scala'] + '-' + command['platform'] + '.log')).open('x') as log:
            child = subprocess.run(command['argv'], cwd=command['cwd'], stdout=log, stderr=subprocess.STDOUT, timeout=1800)
        row = dict(scala=command['scala'], platform=command['platform'], actualExit=child.returncode)
        print(json.dumps(row), flush=True)
        return row

    with ThreadPoolExecutor(max_workers=2) as pool:
        results = list(pool.map(run, commands))
    changed = [row['path'] for row in inputs if sha(Path(row['path'])) != row['sha256']]
    (out / 'completion.json').write_text(json.dumps(dict(lanes=results, inputsChanged=changed), indent=2) + '\n')
    assert not changed and all(row['actualExit'] == 0 for row in results), results
    lanes = [audit(command, out) for command in commands]
    runs = [case['run'] for lane in lanes for case in lane['cases'] if case['run'] is not None]
    assert len(set(runs)) == len(runs)
    report = dict(lanes=lanes, contexts=sum(len(lane['cases']) for lane in lanes),
                  bodyTests=sum(case['tests'] for lane in lanes for case in lane['cases']), executionProcesses=len(runs))
    (out / 'audit.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps({key: value for key, value in report.items() if key != 'lanes'}), flush=True)


if __name__ == '__main__':
    main()
