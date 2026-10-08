#!/usr/bin/env python3
from collections import Counter
import json
from pathlib import Path
import re

from verify import prepare


import sys
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from fixture_harness import checked_lanes
from fixture_targets import target_parser, prepare_lanes


def prepare_lane(args, compiler, platform, paths):
    command, prepared = prepare(args, compiler, platform, paths)
    build = Path(command['cwd'])
    (build / 'project/ProductionInterruption.scala').unlink()
    definition = build / 'build.sbt'
    text = definition.read_text().replace('ProductionJsInterruptionPlugin', 'DistageTestkitJsPlugin').replace('ProductionNativeInterruptionPlugin', 'DistageTestkitNativePlugin')
    text = text.replace('val common = Seq(', '''val expectInspectionListFailure = taskKey[Unit]("Require rejected list selection")
val expectInspectionPlanFailure = taskKey[Unit]("Require rejected plan selection")
val markCandidateExecution = taskKey[Unit]("Mark the end of nonexecuting inspection commands")
val common = Seq(
  markCandidateExecution := Def.uncached { println("SDK_INSPECTION_COMPLETE") },
  expectInspectionListFailure := Def.uncached {
    (Test / distageList).toTask(" --suite-id absent").result.value match {
      case Result.Inc(_) => println("SDK_EXPECTED_LIST_REJECTION")
      case Result.Value(_) => throw new IllegalStateException("Unknown inspection suite unexpectedly resolved")
    }
  },
  expectInspectionPlanFailure := Def.uncached {
    (Test / distagePlan).toTask(" --suite-id absent").result.value match {
      case Result.Inc(_) => println("SDK_EXPECTED_PLAN_REJECTION")
      case Result.Value(_) => throw new IllegalStateException("Unknown inspection suite unexpectedly planned")
    }
  },''')
    definition.write_text(text)
    target = 'candidate-' + ('sjs1' if platform == 'js' else 'native0.5')
    test = dict(target=target, suite='logical:candidate.SuiteB', path=['equal display name', 'should', 'third'], variant=None)
    encoded = json.dumps(json.dumps(test, separators=(',', ':')))
    cases = [platform + '/Test/distageList', platform + '/Test/distagePlan',
             platform + '/Test/distageList --suite-id logical:candidate.SuiteB',
             platform + '/Test/distagePlan --suite-id logical:candidate.SuiteB --memoization disabled',
             platform + '/Test/distageList --test-id ' + encoded,
             platform + '/Test/distagePlan --test-id ' + encoded + ' --memoization disabled',
             platform + '/expectInspectionListFailure', platform + '/expectInspectionPlanFailure',
             platform + '/markCandidateExecution',
             platform + '/testFull']
    command['argv'] = command['argv'][:command['argv'].index(platform + '/testFull')] + cases
    return command, prepared

def main():
    parser = target_parser()
    parser.add_argument('--host-threads', choices=['1', '2'], required=True)
    parser.add_argument('--logical-suite-alias', action='store_true', required=True)
    args = parser.parse_args()
    commands, inputs, out = prepare_lanes(args, prepare_lane, [])
    (out / 'commands.json').write_text(json.dumps(dict(inputs=inputs, commands=commands), indent=2) + '\n')
    checked_lanes(commands, out, inputs, 1200, 0)
    lanes = []
    for command in commands:
        log = (out / (command['scala'] + '-' + command['platform'] + '.log')).read_text()
        lines = log.splitlines()
        responses = [json.loads(line.split('DISTAGE_INSPECTION ', 1)[1]) for line in lines if 'DISTAGE_INSPECTION ' in line]
        assert len(responses) == 6
        assert [response['message']['kind'] for response in responses] == ['resolved', 'planned'] * 3
        selections = [response['message']['selection'] if response['message']['kind'] == 'resolved' else response['message']['plan']['selection'] for response in responses]
        tests = [selection['tests'] for selection in selections]
        assert [len(values) for values in tests] == [15, 15, 3, 3, 1, 1]
        identity = lambda values: {json.dumps(value['id'], sort_keys=True) for value in values}
        assert identity(tests[0]) == identity(tests[1])
        assert identity(tests[2]) == identity(tests[3])
        assert identity(tests[4]) == identity(tests[5])
        assert all(value['id']['suite'] == 'logical:candidate.SuiteB' for values in tests[2:] for value in values)
        assert tests[4][0]['id']['path'][-1] == 'third'
        assert all(not value['settings']['memoization'] for index in [3, 5] for value in tests[index])
        for marker in ['SDK_EXPECTED_LIST_REJECTION', 'SDK_EXPECTED_PLAN_REJECTION']:
            assert lines.count(marker) == 1
        assert lines.count('SDK_INSPECTION_COMPLETE') == 1
        boundary = log.index('SDK_INSPECTION_COMPLETE')
        assert 'TARGET_BODY' not in log[:boundary] and 'TARGET_HELD_ACQUIRE' not in log[:boundary]
        assert Counter(re.findall(r'TARGET_BODY suite=(\S+) test=(\d+) marker=(\d+)', log)) == Counter((f'candidate.Suite{letter}', str(index), str(marker)) for marker, letter in enumerate('ABCDE', 1) for index in [1, 2, 3])
        assert log.count('TARGET_HELD_ACQUIRE') == log.count('TARGET_HELD_RELEASE') == 5
        assert 'Incomplete runs:' not in log and 'RunTerminatedException' not in log and 'RPCCore$ClosedException' not in log
        lanes.append(dict(scala=command['scala'], platform=command['platform'], inspections=6, rejectedSelections=2, executionBodies=15))
    report = dict(lanes=lanes, successfulInspections=6 * len(lanes), rejectedSelections=2 * len(lanes), physicalExecutionBodies=15 * len(lanes))
    (out / 'audit.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps({key: value for key, value in report.items() if key != 'lanes'}), flush=True)


if __name__ == '__main__':
    main()
