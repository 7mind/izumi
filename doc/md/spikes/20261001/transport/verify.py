#!/usr/bin/env python3
"""Run the JS/Native per-suite projection matrix on real SBT, Node and native processes.

Every step's target bodies, CHECK resource lines, projection traces, per-suite JUnit
files, host results, `show` output, suite digests, and the SBT exit code are compared
with explicit expectations; any mismatch makes the driver exit 1.

    export TRANSPORT_LLVM_PATH=...   # see REPORT.md
    direnv exec /home/pavel/work/safe/7mind/izumi python3 verify.py [invocation-prefix ...]
"""
import collections
import json
import os
import re
import shutil
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from dataclasses import dataclass, field
from pathlib import Path

BASE = Path(__file__).resolve().parent
FIXTURE = BASE / 'fixture'
COLLECT = FIXTURE / 'target' / 'spike-collect'
SUITES = tuple(f'transport.Suite{c}' for c in 'ABCDE')
A, B, C, D, E = SUITES
TESTS = ('test1', 'test2', 'test3')
SBT1, SBT2 = '1.13.0', '2.0.9'
PLATFORMS = ('js', 'native')
AGGREGATE_PROPERTY = '-Dtransport.projection=aggregate'
INVOCATION_TIMEOUT_SECONDS = 2400
SUITE_SELECTOR_CASE = '(It is not a test it is a sbt.testing.SuiteSelector)'
ANSI = re.compile(r'\x1b\[[0-9;?]*[A-Za-z]')

BODY = re.compile(r'^BODY (transport\.Suite[A-E]#test\d)$')
CHECK = re.compile(r'CHECK suites=(\S+) bodies=(\d+) acquire=(\d+) release=(\d+) platform=(\w+) pid=(\d+)')
GROUP = re.compile(r'^PROJECTION_GROUP group=(\S+) representative=(\S+) suites=(\S+)$')
LAUNCH = re.compile(r'^PROJECTION_LAUNCH group=(\S+) suite=(\S+) thread=(\S+)$')
WAIT = re.compile(r'^PROJECTION_WAIT group=(\S+) suite=(\S+) thread=(\S+)$')
EMIT = re.compile(r'^PROJECTION_EMIT suite=(\S+) events=(\d+) error=(true|false) thread=(\S+)$')
MODE = re.compile(r'^PROJECTION_MODE (per-suite|aggregate) project=(\S+)')
RESULT = re.compile(r'^SUITE_RESULT (\S+) (Passed|Failed|Error) ')
OVERALL = re.compile(r'^OVERALL_RESULT (\S+) suites=(\d+)$')
DIGEST = re.compile(r'(transport\.Suite[A-E]) -> (sha256-[0-9a-f]+/\d+)')
SHOWN = re.compile(r'(transport\.Suite[A-E]) -> sbt\.SuiteResult')
SUMMARY = re.compile(r'((?:Passed|Failed|Error): Total \d+.*|No tests to run.*)$')
COLLECTED = re.compile(r'^SPIKE_COLLECT (\S+)$')
REPORT_DIR = re.compile(r'^SPIKE_REPORT_DIR (\S+) (\S+)$')


@dataclass(frozen=True)
class Case:
    classname: str
    name: str
    outcome: str


@dataclass
class Expect:
    bodies: collections.Counter
    checks: list
    projection: object  # list of suite tuples per wrapper group; None means aggregate-only mode
    reports: dict
    results: object = None
    overall: object = None
    shown: object = None
    digests: bool = False
    aborts: int = 0
    exits: int = 0
    errors: bool = False
    serial: bool = False


@dataclass
class Step:
    label: str
    commands: list
    expect: Expect


@dataclass
class Invocation:
    name: str
    version: str
    properties: list
    steps: list
    exit_code: int
    facts: dict = field(default_factory=dict)


def bodies(*suites, tests=TESTS):
    return collections.Counter(f'{suite}#{test}' for suite in suites for test in tests)


def cases(suite, tests=TESTS, failing=()):
    return [Case(suite, test, 'failure' if test in failing else 'success') for test in tests]


def passing_reports(*suites):
    return {suite: cases(suite) for suite in suites}


def aggregate_report(*suites):
    return {suites[0]: [case for suite in suites for case in cases(suite)]}


def full_run(version):
    return 'test' if version == SBT1 else 'testFull'


def set_argument(project, argument):
    return f'set {project} / Test / testOptions += Tests.Argument("{argument}")'


def show_digests(project):
    return f'show {project} / Test / definedTestDigests'


def quick_policy(project):
    return (f'set {project} / Test / testQuick / testFilter := {{ val inherited = ({project} / Test / testQuick / testFilter).value; '
            'args => { val selected = Defaults.selectedFilter(args); Seq((name: String) => '
            'if (name.startsWith("transport.Suite")) selected.exists(_(name)) else inherited(args).exists(_(name))) } }')


ALL = list(SUITES)
FAIL_B_REPORTS = {**passing_reports(A, C, D, E), B: cases(B, failing=('test1',))}
FAIL_B_RESULTS = {**dict.fromkeys([A, C, D, E], 'Passed'), B: 'Failed'}
ABORT_REPORTS = {suite: [Case(suite, SUITE_SELECTOR_CASE, 'error')] for suite in SUITES}


def none_run():
    return Expect(collections.Counter(), [], [], {})


def full_expect(groups, **extra):
    return Expect(bodies(*SUITES), groups, groups, passing_reports(*SUITES), **extra)


def main_steps(version, project):
    one = [tuple(SUITES)]
    two = [(A, B, C), (D, E)]
    return [
        Step(f'{project}-testonly-ab', [f'{project}/testOnly {A} {B}'],
             Expect(bodies(A, B), [(A, B)], [(A, B)], passing_reports(A, B))),
        Step(f'{project}-full', [f'{project}/{full_run(version)}'], full_expect(one)),
        Step(f'{project}-show-execute', [f'show {project} / Test / executeTests'], full_expect(one, shown=tuple(SUITES))),
        Step(f'{project}-results', [f'{project}/suiteResults'],
             full_expect(one, results=dict.fromkeys(SUITES, 'Passed'), overall='Passed')),
        Step(f'{project}-two-groups', [f'{project}/testOnly * -- two-groups'], full_expect(two)),
        Step(f'{project}-two-groups-results', [set_argument(project, 'two-groups'), f'{project}/suiteResults'],
             full_expect(two, results=dict.fromkeys(SUITES, 'Passed'), overall='Passed')),
        Step(f'{project}-serial-two-groups-results', [f'set {project} / Test / parallelExecution := false', f'{project}/suiteResults'],
             full_expect(two, results=dict.fromkeys(SUITES, 'Passed'), overall='Passed', serial=True)),
    ]


def fail_b_steps(project):
    one = [tuple(SUITES)]
    return [
        Step(f'{project}-fail-b-show', [set_argument(project, 'fail-b'), f'show {project} / Test / executeTests'],
             Expect(bodies(*SUITES), one, one, FAIL_B_REPORTS, shown=tuple(SUITES))),
        Step(f'{project}-fail-b-results', [f'{project}/suiteResults'],
             Expect(bodies(*SUITES), one, one, FAIL_B_REPORTS, results=FAIL_B_RESULTS, overall='Failed')),
    ]


def abort_steps(project):
    one = [tuple(SUITES)]
    return [
        Step(f'{project}-abort-results', [set_argument(project, 'abort-group'), f'{project}/suiteResults'],
             Expect(collections.Counter(), [], one, ABORT_REPORTS, results=dict.fromkeys(SUITES, 'Error'),
                    overall='Error', aborts=1, errors=True)),
        Step(f'{project}-abort-show', [f'show {project} / Test / executeTests'],
             Expect(collections.Counter(), [], one, ABORT_REPORTS, shown=tuple(SUITES), aborts=1, errors=True)),
    ]


def exit_invocation(version, tag, project):
    """Target process death before any event; Native's adapter tolerates the dead worker in done(), JS's does not."""
    exits = Expect(collections.Counter(), [], [tuple(SUITES)], ABORT_REPORTS, exits=1, errors=True)
    if project == 'native':
        exits.results, exits.overall = dict.fromkeys(SUITES, 'Error'), 'Error'
    return Invocation(f'{tag}-exit-{project}', version, [], [
        Step(f'{project}-exit-results', [set_argument(project, 'exit-group'), f'{project}/suiteResults'], exits)],
        0 if project == 'native' else 1)


def aggregate_steps(version, project, nonce):
    steps = [
        Step(f'{project}-aggregate-testonly-ab', [f'{project}/testOnly {A} {B}'],
             Expect(bodies(A, B), [(A, B)], None, aggregate_report(A, B))),
        Step(f'{project}-aggregate-results', [f'{project}/suiteResults'],
             Expect(bodies(*SUITES), [tuple(SUITES)], None, aggregate_report(*SUITES), results={A: 'Passed'}, overall='Passed')),
    ]
    if version == SBT2:
        steps += [
            Step(f'{project}-aggregate-history-full', [set_argument(project, nonce), show_digests(project), f'{project}/testFull'],
                 Expect(bodies(*SUITES), [tuple(SUITES)], None, aggregate_report(*SUITES), digests=True)),
            Step(f'{project}-aggregate-history-incremental', [f'{project}/test'],
                 Expect(bodies(B, C, D, E), [(B, C, D, E)], None, aggregate_report(B, C, D, E))),
        ]
    return steps


def quick_steps(project):
    first_a = Expect(bodies(A, tests=('test1',)), [(A,)], [(A,)], {A: cases(A, tests=('test1',))})
    return [
        Step(f'{project}-quick-full', [f'{project}/test'], full_expect([tuple(SUITES)])),
        Step(f'{project}-quick-repeat', [f'{project}/testQuick'], none_run()),
        Step(f'{project}-quick-stock-partial', [f'{project}/testOnly {A} -- first-only'], first_a),
        Step(f'{project}-quick-stock-after-partial', [f'{project}/testQuick {A}'], none_run()),
        Step(f'{project}-quick-stock-changed-args', [f'{project}/testQuick {A} -- changed-arg'], none_run()),
        Step(f'{project}-quick-policy-partial', [quick_policy(project), f'{project}/testOnly {A} -- first-only'], first_a),
        Step(f'{project}-quick-policy-after-partial', [f'{project}/testQuick {A}'],
             Expect(bodies(A), [(A,)], [(A,)], passing_reports(A))),
        Step(f'{project}-quick-policy-unfiltered', [f'{project}/testQuick'], full_expect([tuple(SUITES)])),
    ]


def history_success_steps(project, nonce_full, nonce_partial):
    first_a = Expect(bodies(A, tests=('test1',)), [(A,)], [(A,)], {A: cases(A, tests=('test1',))}, digests=True)
    return [
        Step(f'{project}-history-full', [set_argument(project, nonce_full), show_digests(project), f'{project}/testFull'],
             full_expect([tuple(SUITES)], digests=True)),
        Step(f'{project}-history-incremental', [f'{project}/test'], none_run()),
        Step(f'{project}-history-partial', [set_argument(project, nonce_partial), show_digests(project),
                                            f'{project}/testOnly {A} -- first-only'], first_a),
        Step(f'{project}-history-after-partial', [f'{project}/test {A}'],
             Expect(bodies(A), [(A,)], [(A,)], passing_reports(A))),
        Step(f'{project}-history-after-complete', [f'{project}/test {A}'], none_run()),
        Step(f'{project}-history-changed-args', [f'{project}/test {A} -- changed-arg'],
             Expect(bodies(A), [(A,)], [(A,)], passing_reports(A))),
        Step(f'{project}-history-changed-args-repeat', [f'{project}/test {A} -- changed-arg'], none_run()),
    ]


def history_failure_invocations(version, tag, project, nonce):
    """A full run with persistent B failure, then the incremental command in a new SBT process."""
    digests = version == SBT2
    prefix = [set_argument(project, 'fail-b')] + ([set_argument(project, nonce), show_digests(project)] if digests else [])
    incremental = 'test' if digests else 'testQuick'
    full = Invocation(f'{tag}-history-fail-full-{project}', version, [], [
        Step(f'{project}-fail-b-full', prefix + [f'{project}/{full_run(version)}'],
             Expect(bodies(*SUITES), [tuple(SUITES)], [tuple(SUITES)], FAIL_B_REPORTS, digests=digests))], 1)
    rerun = Invocation(f'{tag}-history-fail-incremental-{project}', version, [], [
        Step(f'{project}-fail-b-incremental', prefix + [f'{project}/{incremental}'],
             Expect(bodies(B), [(B,)], [(B,)], {B: cases(B, failing=('test1',))}, digests=digests))], 1)
    return [full, rerun]


def scala390_steps(project):
    return [
        Step(f'{project}-390-testonly-ab', [f'{project}/testOnly {A} {B}'],
             Expect(bodies(A, B), [(A, B)], [(A, B)], passing_reports(A, B))),
        Step(f'{project}-390-full', [f'{project}/testFull'], full_expect([tuple(SUITES)])),
        Step(f'{project}-390-results', [f'{project}/suiteResults'],
             full_expect([tuple(SUITES)], results=dict.fromkeys(SUITES, 'Passed'), overall='Passed')),
    ]


def matrix(run_id):
    def nonce(*parts):
        return '-'.join(('history', run_id) + parts)

    invocations = []
    for version, tag in ((SBT1, 'sbt1'), (SBT2, 'sbt2')):
        invocations += [
            Invocation(f'{tag}-main', version, [], [s for p in PLATFORMS for s in main_steps(version, p)], 0),
            Invocation(f'{tag}-fail-b', version, [], [s for p in PLATFORMS for s in fail_b_steps(p)], 0),
            Invocation(f'{tag}-abort', version, [], [s for p in PLATFORMS for s in abort_steps(p)], 0),
            Invocation(f'{tag}-aggregate', version, [AGGREGATE_PROPERTY],
                       [s for p in PLATFORMS for s in aggregate_steps(version, p, nonce(p, 'aggregate'))], 0),
        ]
        invocations += [exit_invocation(version, tag, p) for p in PLATFORMS]
        if version == SBT1:
            invocations.append(Invocation('sbt1-quick', SBT1, [], [s for p in PLATFORMS for s in quick_steps(p)], 0))
            for p in PLATFORMS:
                invocations += history_failure_invocations(SBT1, tag, p, nonce(p, 'fail-b'))
    invocations.append(Invocation('sbt2-history-success', SBT2, [],
                                  [s for p in PLATFORMS for s in history_success_steps(p, nonce(p, 'full'), nonce(p, 'partial'))], 0))
    for p in PLATFORMS:
        invocations += history_failure_invocations(SBT2, 'sbt2', p, nonce(p, 'fail-b'))
    invocations.append(Invocation('sbt2-scala390', SBT2, [], [Step('scala-390', ['set ThisBuild / scalaVersion := "3.9.0"'], none_run())] +
                                  [s for p in PLATFORMS for s in scala390_steps(p)], 0))
    return invocations


def parse_reports(directory):
    reports = {}
    if not directory.exists():
        return reports
    for path in sorted(directory.glob('*/TEST-*.xml')):
        root = ET.parse(path).getroot()
        found = []
        for testcase in root.findall('testcase'):
            outcome = 'failure' if testcase.find('failure') is not None else 'error' if testcase.find('error') is not None else 'success'
            found.append(Case(testcase.attrib['classname'], testcase.attrib['name'], outcome))
        reports[root.attrib['name']] = {
            'file': str(path.relative_to(directory)), 'tests': int(root.attrib['tests']),
            'failures': int(root.attrib['failures']), 'errors': int(root.attrib['errors']), 'cases': found}
    return reports


def parse_segment(lines, reports_directory):
    def matches(pattern):
        return [m for m in (pattern.match(line) for line in lines) if m]

    shown = None
    for line in lines:
        if 'Output(' in line and SHOWN.search(line):
            shown = sorted(SHOWN.findall(line))
    digests = {}
    for line in lines:
        if ' -> sha256-' in line:
            digests = dict(DIGEST.findall(line))
    return {
        'bodies': collections.Counter(m.group(1) for m in matches(BODY)),
        'checks': [{'suites': tuple(m.group(1).split(',')), 'bodies': int(m.group(2)), 'acquire': int(m.group(3)),
                    'release': int(m.group(4)), 'platform': m.group(5), 'pid': m.group(6)}
                   for m in (CHECK.search(line) for line in lines) if m],
        'groups': [{'label': m.group(1), 'representative': m.group(2), 'suites': tuple(m.group(3).split(','))} for m in matches(GROUP)],
        'launches': [{'group': m.group(1), 'suite': m.group(2), 'thread': m.group(3)} for m in matches(LAUNCH)],
        'waits': [{'group': m.group(1), 'suite': m.group(2), 'thread': m.group(3)} for m in matches(WAIT)],
        'emits': [{'suite': m.group(1), 'events': int(m.group(2)), 'error': m.group(3) == 'true', 'thread': m.group(4)}
                  for m in matches(EMIT)],
        'modes': sorted({m.group(1) for m in matches(MODE)}),
        'results': {m.group(1): m.group(2) for m in matches(RESULT)},
        'overall': [m.group(1) for m in matches(OVERALL)],
        'shown': shown,
        'digests': digests,
        'aborts': sum(1 for line in lines if line.startswith('ABORT ')),
        'exits': sum(1 for line in lines if line.startswith('EXIT ')),
        'deserialized': sum(1 for line in lines if line.startswith('DESERIALIZE ')),
        'summaries': [m.group(1) for m in (SUMMARY.search(line) for line in lines) if m],
        'reports': parse_reports(reports_directory),
    }


def check_step(step, facts):
    expect = step.expect
    problems = []

    def require(condition, message):
        if not condition:
            problems.append(message)

    require(facts['bodies'] == expect.bodies, f"bodies {dict(facts['bodies'])} != {dict(expect.bodies)}")
    require(sorted(c['suites'] for c in facts['checks']) == sorted(expect.checks),
            f"CHECK groups {[c['suites'] for c in facts['checks']]} != {expect.checks}")
    for check in facts['checks']:
        require(check['acquire'] == 1 and check['release'] == 1, f'resource lifetime {check}')
        expected = sum(n for body, n in expect.bodies.items() if body.split('#')[0] in check['suites'])
        require(check['bodies'] == expected, f'CHECK bodies {check} != {expected}')
    require(facts['aborts'] == expect.aborts, f"ABORT lines {facts['aborts']} != {expect.aborts}")
    require(facts['exits'] == expect.exits, f"EXIT lines {facts['exits']} != {expect.exits}")
    if expect.serial:
        require(not facts['waits'], f"serial scheduling waited: {facts['waits']}")

    if expect.projection is None:
        require(facts['modes'] in ([], ['aggregate']), f"modes {facts['modes']} != aggregate")
        require(not (facts['groups'] or facts['launches'] or facts['waits'] or facts['emits']), 'projection active in aggregate mode')
        require(facts['deserialized'] == len(expect.checks), f"target executions {facts['deserialized']} != {len(expect.checks)}")
    else:
        require(facts['modes'] in ([], ['per-suite']), f"modes {facts['modes']} != per-suite")
        require(sorted(g['suites'] for g in facts['groups']) == sorted(expect.projection),
                f"projection groups {[g['suites'] for g in facts['groups']]} != {expect.projection}")
        launched = collections.Counter(launch['group'] for launch in facts['launches'])
        require(sorted(launched) == sorted(g['label'] for g in facts['groups']) and set(launched.values()) <= {1},
                f'launches {dict(launched)} do not match groups once each')
        require(facts['deserialized'] == len(expect.projection), f"target executions {facts['deserialized']} != {len(expect.projection)}")
        emitted = collections.Counter(emit['suite'] for emit in facts['emits'])
        projected = sorted(s for group in expect.projection for s in group)
        require(sorted(emitted) == projected and set(emitted.values()) <= {1}, f'emits {dict(emitted)} != {projected}')
        for emit in facts['emits']:
            events = sum(n for body, n in expect.bodies.items() if body.split('#')[0] == emit['suite'])
            require(emit['events'] == events and emit['error'] == expect.errors, f'emit {emit} != events={events} error={expect.errors}')

    reports = facts['reports']
    require(sorted(reports) == sorted(expect.reports), f'report files {sorted(reports)} != {sorted(expect.reports)}')
    for suite, expected_cases in expect.reports.items():
        if suite not in reports:
            continue
        report = reports[suite]
        require(report['cases'] == expected_cases, f'{suite} cases {report["cases"]} != {expected_cases}')
        require(report['tests'] == len(expected_cases), f'{suite} tests={report["tests"]}')
        require(report['failures'] == sum(c.outcome == 'failure' for c in expected_cases), f'{suite} failures={report["failures"]}')
        require(report['errors'] == sum(c.outcome == 'error' for c in expected_cases), f'{suite} errors={report["errors"]}')

    if expect.results is not None:
        require(facts['results'] == expect.results, f"results {facts['results']} != {expect.results}")
        require(facts['overall'] == [expect.overall], f"overall {facts['overall']} != {expect.overall}")
    else:
        require(not facts['results'], f"unexpected results {facts['results']}")
    if expect.shown is not None:
        require(facts['shown'] == sorted(expect.shown), f"show executeTests suites {facts['shown']} != {sorted(expect.shown)}")
    if expect.digests:
        values = facts['digests']
        require(sorted(values) == sorted(SUITES) and len(set(values.values())) == len(SUITES), f'digests not five distinct: {values}')
    return problems


def segments(lines, invocation):
    labels = [s.label for s in invocation.steps]
    found = {}
    current = []
    for line in lines:
        m = COLLECTED.match(line)
        if m:
            found[m.group(1)] = current
            current = []
        else:
            current.append(line)
    trailing = current
    result = {}
    for index, label in enumerate(labels):
        last = index == len(labels) - 1
        if label in found:
            result[label] = found[label]
        elif last and invocation.exit_code != 0:
            result[label] = trailing
    return result, found


def run_invocation(invocation, run_directory, environment):
    commands = ['spikeCollect start'] + [c for step in invocation.steps for c in step.commands + [f'spikeCollect {step.label}']]
    if invocation.exit_code != 0:
        commands = commands[:-1]
    argv = ['sbt', '-batch', '-Dsbt.supershell=false', '-sbt-version', invocation.version] + invocation.properties + commands
    shutil.rmtree(COLLECT, ignore_errors=True)
    started = time.monotonic()
    completed = subprocess.run(argv, cwd=FIXTURE, env=environment, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                               text=True, timeout=INVOCATION_TIMEOUT_SECONDS)
    elapsed = round(time.monotonic() - started, 1)
    (run_directory / f'{invocation.name}.log').write_text(completed.stdout)
    lines = [ANSI.sub('', line).rstrip('\r') for line in completed.stdout.splitlines()]
    report_dirs = {m.group(1): Path(m.group(2)) for m in (REPORT_DIR.match(line) for line in lines) if m}
    if invocation.exit_code != 0:
        for project, directory in report_dirs.items():
            for report in sorted(directory.glob('*.xml')) if directory.exists() else []:
                target = COLLECT / invocation.steps[-1].label / project / report.name
                target.parent.mkdir(parents=True, exist_ok=True)
                shutil.move(str(report), target)
    collected = run_directory / invocation.name
    if COLLECT.exists():
        shutil.move(str(COLLECT), collected)
    by_label, found = segments(lines, invocation)
    problems = {}
    facts = {}
    if completed.returncode != invocation.exit_code:
        problems['invocation'] = [f'exit {completed.returncode} != {invocation.exit_code}']
    if 'start' not in found:
        problems.setdefault('invocation', []).append('spikeCollect start missing')
    for step in invocation.steps:
        if step.label not in by_label:
            problems[step.label] = ['step output missing']
            continue
        facts[step.label] = parse_segment(by_label[step.label], collected / step.label)
        step_problems = check_step(step, facts[step.label])
        if step_problems:
            problems[step.label] = step_problems
        status = 'FAIL' if step_problems else 'PASS'
        f = facts[step.label]
        print(f"{status} {invocation.name} {step.label}: bodies={sum(f['bodies'].values())} checks={len(f['checks'])} "
              f"launches={len(f['launches'])} waits={len(f['waits'])} reports={ {s: r['tests'] for s, r in f['reports'].items()} }"
              + (f' problems={step_problems}' if step_problems else ''), flush=True)
    invocation.facts = facts
    summary = {'name': invocation.name, 'argv': argv, 'exit': completed.returncode, 'expected_exit': invocation.exit_code,
               'elapsed_seconds': elapsed, 'problems': problems, 'steps': facts}
    (run_directory / f'{invocation.name}.json').write_text(json.dumps(summary, indent=2, default=jsonable) + '\n')
    print(f"{'FAIL' if problems else 'PASS'} {invocation.name}: exit={completed.returncode} elapsed={elapsed}s", flush=True)
    return summary


def jsonable(value):
    if isinstance(value, Case):
        return [value.classname, value.name, value.outcome]
    if isinstance(value, Path):
        return str(value)
    raise TypeError(repr(value))


def cross_checks(results):
    problems = []
    for project in PLATFORMS:
        full = results.get(f'sbt2-history-fail-full-{project}')
        incremental = results.get(f'sbt2-history-fail-incremental-{project}')
        if full and incremental:
            before = full['steps'].get(f'{project}-fail-b-full', {}).get('digests')
            after = incremental['steps'].get(f'{project}-fail-b-incremental', {}).get('digests')
            if not before or before != after:
                problems.append(f'{project} fail-b digests differ between testFull and test invocations: {before} / {after}')
    return problems


def main():
    llvm = os.environ.get('TRANSPORT_LLVM_PATH')
    if not llvm:
        sys.exit('TRANSPORT_LLVM_PATH is required for Scala Native linking; see REPORT.md')
    environment = dict(os.environ)
    environment['PATH'] = llvm + os.pathsep + environment['PATH']
    run_id = time.strftime('%Y%m%d-%H%M%S')
    run_directory = BASE / 'logs' / run_id
    run_directory.mkdir(parents=True)
    selected = [i for i in matrix(run_id) if not sys.argv[1:] or any(i.name.startswith(p) for p in sys.argv[1:])]
    print(f'RUN {run_id}: {[i.name for i in selected]}', flush=True)
    results = {}
    for invocation in selected:
        results[invocation.name] = run_invocation(invocation, run_directory, environment)
    cross = cross_checks(results)
    failed = [name for name, summary in results.items() if summary['problems']]
    (run_directory / 'summary.json').write_text(json.dumps(
        {'run': run_id, 'invocations': {n: {'exit': s['exit'], 'problems': s['problems'], 'elapsed_seconds': s['elapsed_seconds']}
                                         for n, s in results.items()}, 'cross_checks': cross}, indent=2) + '\n')
    for problem in cross:
        print(f'FAIL cross-check: {problem}', flush=True)
    if failed or cross:
        print(f'FAIL {run_id}: {failed}', flush=True)
        sys.exit(1)
    print(f'PASS {run_id}: {len(results)} invocations', flush=True)


if __name__ == '__main__':
    main()
