#!/usr/bin/env python3
import argparse
from collections import Counter
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET

from verify import prepare, sha

import sys
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from fixture_harness import load_module, run_lanes

policy = load_module('target_policy', Path(__file__).with_name('verify-policy.py'))


FOREIGN = '''package candidate
import org.scalacheck.{Gen, Prop, Properties}
object ForeignProperties extends Properties("foreign") {
  for (name <- List("first", "second", "third")) {
    property(name) = Prop.forAll(Gen.const(0)) { _ =>
      println("TARGET_FOREIGN_BODY test=" + name)
      true
    }
  }
}
'''


def audit(command, out):
    log = (out / (command['scala'] + '-' + command['platform'] + '.log')).read_text()
    build = Path(command['cwd'])
    records = []
    for case in command['cases']:
        label = case['name']
        segment = log.split('SDK_POLICY_BEGIN ' + label + '\n', 1)[1].split('SDK_POLICY_END ' + label + '\n', 1)[0]
        expected = Counter(('candidate.Suite' + letter, str(index), str('ABCDE'.index(letter) + 1)) for letter in case['owned'] for index in [1, 2, 3])
        assert Counter(re.findall(r'TARGET_BODY suite=(\S+) test=(\d+) marker=(\d+)', segment)) == expected, (label, 'owned bodies')
        assert Counter(re.findall(r'TARGET_FOREIGN_BODY test=(\S+)', segment)) == Counter(['first', 'second', 'third'] if case['foreign'] else []), (label, 'foreign execution count')
        capture = build / 'captures' / label
        owned = Counter()
        foreign = []
        for path in (capture / 'xml').glob('*.xml'):
            xml = ET.parse(path).getroot()
            assert not xml.findall('.//failure') and not xml.findall('.//error'), (label, path)
            names = [item.attrib['name'] for item in xml.findall('.//testcase')]
            if xml.attrib['name'] in ['candidate.ForeignProperties', 'candidate.ForeignProperties-0', 'candidate.ForeignProperties-1', 'candidate.ForeignProperties-2']:
                expected_name = {'candidate.ForeignProperties-0': 'foreign.first', 'candidate.ForeignProperties-1': 'foreign.second', 'candidate.ForeignProperties-2': 'foreign.third'}.get(xml.attrib['name'])
                cases = xml.findall('.//testcase')
                assert names == ([expected_name] if expected_name else []), (label, 'foreign task group', names)
                assert all(item.attrib['classname'] == xml.attrib['name'] for item in cases), (label, 'foreign XML class')
                foreign += names
            else:
                owned.update((xml.attrib['name'], name) for name in names)
        names = {1: 'first', 2: 'second', 3: 'third'}
        wanted = Counter(('candidate.Suite' + letter, 'equal display name should ' + names[index]) for letter in case['owned'] for index in [1, 2, 3])
        assert owned == wanted, (label, 'owned XML', owned, wanted)
        assert Counter(foreign) == Counter(['foreign.first', 'foreign.second', 'foreign.third'] if case['foreign'] else []), (label, 'foreign XML', foreign)
        streams = list((capture / 'frames').glob('*.jsonl'))
        assert len(streams) == int(bool(case['owned'])), (label, 'owned application count')
        run = None
        if streams:
            frames = [json.loads(line) for line in streams[0].read_text().splitlines()]
            assert all(frame['schemaVersion'] == 4 for frame in frames)
            messages = [frame['message'] for frame in frames]
            assert messages[-1]['kind'] == 'completed'
            outcome = messages[-1]['outcome']
            assert not outcome['failures'] and not outcome['cancelled']
            assert len(outcome['results']) == len(wanted)
            assert {item['id']['suite'] for item in outcome['results']} == {'logical:candidate.Suite' + letter for letter in case['owned']}
            assert all(item['status'] == 'succeeded' for item in outcome['results'])
            events = [message for message in messages if message['kind'] == 'event']
            assert [int(event['sequence']) for event in events] == list(range(len(events)))
            identity = lambda ids: Counter(json.dumps(value, sort_keys=True) for value in ids)
            starts = [message['event']['test'] for message in events if message['event']['kind'] == 'testStarted']
            finishes = [message['event']['result']['id'] for message in events if message['event']['kind'] == 'testCompleted']
            assert identity(starts) == identity(finishes) == identity(item['id'] for item in outcome['results'])
            run = outcome['run']
        assert segment.count('TARGET_HELD_ACQUIRE') == segment.count('TARGET_HELD_RELEASE') == len(case['owned'])
        records.append(dict(case=label, owned=len(wanted), foreign=len(foreign), run=run,
                            files=[dict(path=str(path), sha256=sha(path)) for path in capture.rglob('*') if path.is_file()]))
    return dict(scala=command['scala'], platform=command['platform'], cases=records)


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
            (build / 'shared/ForeignProperties.scala').write_text(FOREIGN)
            definition = build / 'build.sbt'
            text = definition.read_text().replace('ProductionJsInterruptionPlugin', 'DistageTestkitJsPlugin').replace('ProductionNativeInterruptionPlugin', 'DistageTestkitNativePlugin')
            text = text.replace('val common = Seq(', policy.CONTROLS + '\nval common = Seq(\n' + policy.SETTINGS + '''
  libraryDependencies += "org.scalacheck" % ("scalacheck_" + candidatePlatform.value + "_" + scalaBinaryVersion.value) % "1.19.0" % Test,
  libraryDependencySchemes ++= (if (candidatePlatform.value == "native0.5") Seq("org.scala-native" % ("test-interface_native0.5_" + scalaBinaryVersion.value) % "always") else Seq.empty),
  Test / testOptions += Tests.Argument(TestFramework("org.scalacheck.ScalaCheckFramework"), "-minSuccessfulTests", "1"),
''')
            text = text.replace('Test / testFrameworks := Seq(TestFramework("izumi.distage.testkit.runner.bootstrap.Framework"))', 'Test / testFrameworks := Seq(TestFramework("izumi.distage.testkit.runner.bootstrap.Framework"), TestFramework("org.scalacheck.ScalaCheckFramework"))')
            definition.write_text(text)
            command['cases'] = [
                dict(name='mixed-full', request='testFull', owned='ABCDE', foreign=True, before=[]),
                dict(name='mixed-incremental', request='test', owned='ABCDE', foreign=False, before=[]),
                dict(name='mixed-quick', request='testQuick', owned='ABCDE', foreign=False, before=[]),
                dict(name='mixed-selected', request='testOnly *SuiteB *ForeignProperties', owned='B', foreign=True, before=[]),
                dict(name='foreign-only', request='testOnly *ForeignProperties', owned='', foreign=True, before=[]),
                dict(name='owned-only', request='testOnly *SuiteA', owned='A', foreign=False, before=[]),
                dict(name='after-partial', request='testQuick', owned='ABCDE', foreign=False, before=[]),
                dict(name='exclude-foreign', request='testFull', owned='ABCDE', foreign=False, before=['set ' + platform + '/policyOptions := Seq(Tests.Exclude(Seq("candidate.ForeignProperties")))']),
                dict(name='reset-full', request='testFull', owned='ABCDE', foreign=True, before=['set ' + platform + '/policyOptions := Seq.empty']),
                dict(name='serial-mixed', request='testFull', owned='ABCDE', foreign=True, before=['set ' + platform + '/Test/parallelExecution := false']),
                dict(name='host-one-mixed', request='testFull', owned='ABCDE', foreign=True, before=['set ' + platform + '/Test/parallelExecution := true', 'set Global / concurrentRestrictions := Seq(Tags.limit(Tags.Test, 1))']),
            ]
            requests = []
            for case in command['cases']:
                requests += case['before'] + [platform + '/preparePolicy ' + case['name'], platform + '/' + case['request'], platform + '/collectPolicy ' + case['name']]
            command['argv'] = command['argv'][:command['argv'].index(platform + '/testFull')] + requests
            inputs += [row for row in prepared if not Path(row['path']).is_relative_to(build)]
            inputs += [dict(path=str(path), sha256=sha(path)) for path in build.rglob('*') if path.is_file()]
            commands.append(command)
    (out / 'commands.json').write_text(json.dumps(dict(inputs=inputs, commands=commands), indent=2) + '\n')

    results = run_lanes(commands, out, 1800)
    changed = [row['path'] for row in inputs if sha(Path(row['path'])) != row['sha256']]
    (out / 'completion.json').write_text(json.dumps(dict(lanes=results, inputsChanged=changed), indent=2) + '\n')
    assert not changed and all(row['actualExit'] == 0 for row in results), results
    lanes = [audit(command, out) for command in commands]
    runs = [case['run'] for lane in lanes for case in lane['cases'] if case['run'] is not None]
    assert len(set(runs)) == len(runs)
    report = dict(lanes=lanes, cases=sum(len(lane['cases']) for lane in lanes),
                  owned=sum(case['owned'] for lane in lanes for case in lane['cases']),
                  foreign=sum(case['foreign'] for lane in lanes for case in lane['cases']), applications=len(runs))
    (out / 'audit.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps({key: value for key, value in report.items() if key != 'lanes'}), flush=True)


if __name__ == '__main__':
    main()
