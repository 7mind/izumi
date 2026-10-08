#!/usr/bin/env python3
from collections import Counter
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET

from verify import prepare, sha


import sys
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from fixture_harness import checked_lanes, execution_stream
from fixture_targets import target_parser, prepare_lanes

CONTEXT_SETTINGS = '''
  Test / target := baseDirectory.value / "target/test",
  Integration / target := baseDirectory.value / "target/it",
  Integration / unmanagedSourceDirectories := (Test / unmanagedSourceDirectories).value,
  Integration / testFrameworks := (Test / testFrameworks).value,
  Test / distageTargetId := "candidate-" + thisProject.value.id + "/test",
  Integration / distageBuildId := "candidate",
  Integration / distageTargetId := "candidate-" + thisProject.value.id + "/it",
  Integration / distageCatalogueId := "candidate",
  Integration / distageEventDirectory := file(sys.props("candidate.frames")),
  Test / testListeners := Seq(new sbt.JUnitXmlTestsListener(((Test / target).value / "explicit").getAbsolutePath)),
  Integration / testListeners := Seq(new sbt.JUnitXmlTestsListener(((Integration / target).value / "explicit").getAbsolutePath)),
'''


def prepare_build(args, compiler, platform, paths):
    command, prepared = prepare(args, compiler, platform, paths)
    build = Path(command['cwd'])
    (build / 'project/ProductionInterruption.scala').unlink()
    definition = build / 'build.sbt'
    text = definition.read_text().replace('ProductionJsInterruptionPlugin', 'DistageTestkitJsPlugin').replace('ProductionNativeInterruptionPlugin', 'DistageTestkitNativePlugin')
    text = 'import izumi.distage.sbt.DistageTestkitJsPlugin.autoImport.*\nimport izumi.distage.sbt.DistageTestkitNativePlugin.autoImport.*\n' + text
    text = text.replace('val common = Seq(', 'lazy val Integration = config("it").extend(Test)\nval common = Seq(')
    modules = []
    for prefix, settings in [('js', 'distageJsTestSettings'), ('native', 'distageNativeTestSettings')]:
        start = text.index('lazy val ' + prefix + ' = ')
        end = text.index('\n)\n', start) + 3
        original = text[start:end]
        updated = original.replace('.settings(common).settings(', '.configs(Integration).settings(common).settings(inConfig(Integration)(Defaults.testSettings ++ ' + settings + ')).settings(\n' + CONTEXT_SETTINGS)
        assert updated != original
        text = text[:start] + updated + text[end:]
        modules.append(updated.replace('lazy val ' + prefix + ' =', 'lazy val ' + prefix + 'Twin =').replace('file("' + prefix + '")', 'file("' + prefix + 'Twin")'))
    text += '\n' + '\n'.join(modules)
    text += '''
lazy val groupedJs = project.in(file("grouped-js")).configs(Integration).aggregate(js, jsTwin).settings(inConfig(Integration)(Defaults.testSettings))
lazy val groupedNative = project.in(file("grouped-native")).configs(Integration).aggregate(native, nativeTwin).settings(inConfig(Integration)(Defaults.testSettings))
val prepareConfiguration = inputKey[Unit]("Prepare all module/configuration captures")
val collectConfiguration = inputKey[Unit]("Freeze all module/configuration captures")
prepareConfiguration / aggregate := false
collectConfiguration / aggregate := false
prepareConfiguration := {
  val labels = spaceDelimited("case").parsed
  require(labels.size == 1, "Expected a configuration case")
  val targets = Vector(TARGETS)
  targets.foreach(path => IO.delete(path / "explicit" / "test-reports"))
  IO.delete(file(sys.props("candidate.frames")))
  println("SDK_CONFIGURATION_BEGIN " + labels.head)
}
collectConfiguration := {
  val labels = spaceDelimited("case").parsed
  require(labels.size == 1, "Expected a configuration case")
  val destination = file(sys.props("candidate.captures")) / labels.head
  require(!destination.exists(), "Configuration capture already exists")
  val targets = Vector(NAMED_TARGETS)
  IO.createDirectory(destination)
  targets.foreach { case (label, path) => IO.copyDirectory(path / "explicit" / "test-reports", destination / label) }
  IO.copyDirectory(file(sys.props("candidate.frames")), destination / "frames")
  println("SDK_CONFIGURATION_END " + labels.head)
}
'''
    contexts = [(module, config) for module in ['js', 'jsTwin', 'native', 'nativeTwin'] for config in ['Test', 'Integration']]
    text = text.replace('NAMED_TARGETS', ', '.join('("' + module + '-' + ('test' if config == 'Test' else 'it') + '", (' + module + ' / ' + config + ' / target).value)' for module, config in contexts))
    text = text.replace('TARGETS', ', '.join('(' + module + ' / ' + config + ' / target).value' for module, config in contexts))
    definition.write_text(text)
    group = 'groupedJs' if platform == 'js' else 'groupedNative'
    pair = [platform, platform + 'Twin']
    expected = lambda modules, config, letters: [(module, config, letter, index) for module in modules for letter in letters for index in [1, 2, 3]]
    single = dict(target='candidate-' + platform + '/it', suite='logical:candidate.SuiteB', path=['equal display name', 'should', 'third'], variant=None)
    encoded = json.dumps(json.dumps(single, separators=(',', ':')))
    command['cases'] = [
        dict(name='aggregate-full', requests=[group + '/testFull'], tests=expected(pair, 'test', 'ABCDE')),
        dict(name='aggregate-selected', requests=[group + '/testOnly *SuiteB *SuiteD'], tests=expected(pair, 'test', 'BD')),
        dict(name='aggregate-complete', requests=[group + '/testQuick'], tests=expected(pair, 'test', 'ABCDE')),
        dict(name='integration-selected', requests=[group + '/Integration/testOnly *SuiteB *SuiteD'], tests=expected(pair, 'it', 'BD')),
        dict(name='integration-full', requests=[group + '/Integration/testFull'], tests=expected(pair, 'it', 'ABCDE')),
        dict(name='one-module', requests=[platform + '/testOnly *SuiteA'], tests=expected([platform], 'test', 'A')),
        dict(name='integration-quick', requests=[group + '/Integration/testQuick *SuiteD'], tests=expected(pair, 'it', 'D')),
        dict(name='integration-individual', requests=[platform + '/Integration/testOnly *SuiteB -- --test-id ' + encoded], tests=[(platform, 'it', 'B', 3)]),
        dict(name='inspection', requests=[module + '/' + config + '/' + operation for module in pair for config in ['Test', 'Integration'] for operation in ['distageList', 'distagePlan']], tests=[]),
        dict(name='after-inspection', requests=[group + '/testFull'], tests=expected(pair, 'test', 'ABCDE')),
        dict(name='integration-recovery', requests=[group + '/Integration/test'], tests=expected(pair, 'it', 'ABCDE')),
    ]
    requests = []
    for case in command['cases']:
        requests += ['prepareConfiguration ' + case['name']] + case['requests'] + ['collectConfiguration ' + case['name']]
    command['argv'] = command['argv'][:command['argv'].index(platform + '/testFull')] + requests
    inputs = [row for row in prepared if not Path(row['path']).is_relative_to(build)]
    inputs += [dict(path=str(path), sha256=sha(path)) for path in build.rglob('*') if path.is_file()]
    return command, inputs


def audit(command, out):
    build = Path(command['cwd'])
    log = (out / (command['scala'] + '-' + command['platform'] + '.log')).read_text()
    records = []
    for case in command['cases']:
        label = case['name']
        begin, end = 'SDK_CONFIGURATION_BEGIN ' + label + '\n', 'SDK_CONFIGURATION_END ' + label + '\n'
        assert log.count(begin) == log.count(end) == 1
        segment = log.split(begin, 1)[1].split(end, 1)[0]
        expected = Counter(('candidate.Suite' + letter, str(index), str('ABCDE'.index(letter) + 1)) for _, _, letter, index in case['tests'])
        assert Counter(re.findall(r'TARGET_BODY suite=(\S+) test=(\d+) marker=(\d+)', segment)) == expected, (label, 'physical bodies')
        held = sum(index == 3 for _, _, _, index in case['tests'])
        assert segment.count('TARGET_HELD_ACQUIRE') == segment.count('TARGET_HELD_RELEASE') == held
        capture = build / 'captures' / label
        names = {1: 'first', 2: 'second', 3: 'third'}
        actual_xml = Counter()
        for directory in capture.iterdir():
            if directory.name == 'frames':
                continue
            module, config = directory.name.rsplit('-', 1)
            for file in directory.glob('*.xml'):
                xml = ET.parse(file).getroot()
                assert not xml.findall('.//failure') and not xml.findall('.//error'), (label, file)
                for item in xml.findall('.//testcase'):
                    actual_xml[(module, config, xml.attrib['name'], item.attrib['name'])] += 1
        expected_xml = Counter((module, config, 'candidate.Suite' + letter, 'equal display name should ' + names[index]) for module, config, letter, index in case['tests'])
        assert actual_xml == expected_xml, (label, 'configuration XML', actual_xml, expected_xml)
        streams = []
        actual_ids = Counter()
        for file in (capture / 'frames').glob('*.jsonl'):
            events, outcome = execution_stream(file.read_text())
            assert not outcome['cancelled'] and not outcome['failures']
            assert all(item['status'] == 'succeeded' for item in outcome['results'])
            targets = {item['id']['target'] for item in outcome['results']}
            assert len(targets) == 1, (label, 'cross-configuration sharing')
            identity = lambda values: Counter(json.dumps(value, sort_keys=True) for value in values)
            starts = [message['event']['test'] for message in events if message['event']['kind'] == 'testStarted']
            finishes = [message['event']['result']['id'] for message in events if message['event']['kind'] == 'testCompleted']
            assert identity(starts) == identity(finishes) == identity(item['id'] for item in outcome['results'])
            actual_ids.update((item['id']['target'], item['id']['suite'], *item['id']['path']) for item in outcome['results'])
            streams.append(dict(run=outcome['run'], target=next(iter(targets)), path=str(file), sha256=sha(file)))
        expected_ids = Counter(('candidate-' + module + '/' + config, 'logical:candidate.Suite' + letter, 'equal display name', 'should', names[index]) for module, config, letter, index in case['tests'])
        assert actual_ids == expected_ids, (label, 'target identities')
        assert len(streams) == len({(module, config) for module, config, _, _ in case['tests']})
        if label == 'inspection':
            responses = [json.loads(line.split('DISTAGE_INSPECTION ', 1)[1]) for line in segment.splitlines() if 'DISTAGE_INSPECTION ' in line]
            assert len(responses) == 8 and [value['message']['kind'] for value in responses] == ['resolved', 'planned'] * 4
            for response in responses:
                selection = response['message']['selection'] if response['message']['kind'] == 'resolved' else response['message']['plan']['selection']
                assert len(selection['tests']) == 15
            assert 'TARGET_BODY' not in segment and 'TARGET_HELD_ACQUIRE' not in segment
        records.append(dict(case=label, tests=len(case['tests']), streams=streams))
    return dict(scala=command['scala'], platform=command['platform'], cases=records)


def prepare_lane(args, compiler, platform, paths):
    command, prepared = prepare_build(args, compiler, platform, paths)
    return command, prepared

def main():
    parser = target_parser()
    args = parser.parse_args()
    args.host_threads, args.logical_suite_alias = '2', True
    commands, inputs, out = prepare_lanes(args, prepare_lane, [])
    (out / 'commands.json').write_text(json.dumps(dict(inputs=inputs, commands=commands), indent=2) + '\n')

    checked_lanes(commands, out, inputs, 1800, 0)
    lanes = [audit(command, out) for command in commands]
    streams = [stream for lane in lanes for case in lane['cases'] for stream in case['streams']]
    assert len({stream['run'] for stream in streams}) == len(streams)
    result = dict(lanes=lanes, contexts=sum(len(lane['cases']) for lane in lanes),
                  tests=sum(case['tests'] for lane in lanes for case in lane['cases']), applications=len(streams))
    (out / 'audit.json').write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps({key: value for key, value in result.items() if key != 'lanes'}), flush=True)


if __name__ == '__main__':
    main()
