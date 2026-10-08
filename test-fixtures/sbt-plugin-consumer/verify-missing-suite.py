#!/usr/bin/env python3
from pathlib import Path
from xml.etree import ElementTree
import argparse
import os
import uuid

import sys
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from fixture_delivery import DeliveryControl, delivery_framework, delivery_settings, run_delivery

ROOT = Path(__file__).resolve().parents[2]
TIMEOUT_SECONDS = 600
PREFIX = 'izumi.fixtures.host.'

CONTROL = DeliveryControl(
    project='missingSuiteConsumer',
    framework='OmittingFramework',
    command='MissingSuite',
    fault_mode='missing',
    fault_flag='omit.flag',
    label='omission',
    run_label='missing-suite',
    rejection='TARGET_OMISSION_FALSE_SUCCESS',
)
FRAMEWORK = delivery_framework(CONTROL, r'''        val admitted = if (Files.isRegularFile(audit.resolve("omit.flag"))) definitions.filterNot(_.fullyQualifiedName() == "izumi.fixtures.host.SuiteD") else definitions
        original.tasks(admitted).map { task => new Task {
          override def taskDef(): TaskDef = task.taskDef()
          override def tags(): Array[String] = task.tags()
          override def execute(handler: EventHandler, loggers: Array[Logger]): Array[Task] = task.execute(handler,loggers)
        }}''')
SETTINGS = delivery_settings(CONTROL)

def verify_case(lane, row, args, resources, parents, children):
    case = lane/'cases'/row['name']; audit = case/'audit'; missing = row['mode']=='missing'
    labels = ['SuiteC','SuiteE'] if missing else ['SuiteC','SuiteD','SuiteE']
    bodies = [p.read_text().split('\t') for p in audit.glob('*.body')]
    assert sorted((r[0],r[1]) for r in bodies)==sorted((PREFIX+s,str(i)) for s in labels for i in range(1,4))
    acquired = [p.read_text() for p in audit.glob('*.acquire')]; released = [p.read_text() for p in audit.glob('*.release')]
    assert len(acquired)==1 and acquired==released and not resources.intersection(acquired)
    resources.update(acquired)
    selected = (audit/'target.selected').read_text().splitlines(); target_pid = selected[0]
    assert selected[1:]==[PREFIX+s for s in ['SuiteC','SuiteD','SuiteE']]
    reports = [ElementTree.parse(p) for p in (case/'test-reports').glob('*.xml')]
    passed = [(n.attrib['classname'],n.attrib['name']) for x in reports for n in x.findall('.//testcase') if n.find('error') is None]
    assert sorted(passed)==sorted((PREFIX+s,'equal display name should '+leaf) for s in labels for leaf in ['first','second','third'])
    errors = [n for x in reports for n in x.findall('.//testcase') if n.find('error') is not None]
    assert all(not x.findall('.//failure') and not x.findall('.//skipped') for x in reports)
    if missing:
        assert (audit/'host.rejected').is_file()
        if args.expected_missing_report=='absent':
            assert not errors and 'Incomplete distage host result' in (audit/'host.rejected').read_text()
        else:
            assert len(errors)==1 and errors[0].attrib['classname']==PREFIX+'SuiteD'
            assert 'terminal' in ''.join(errors[0].itertext()).lower()
    else: assert not errors and not (audit/'host.rejected').exists()
    terminals = []
    if args.expected_missing_report=='error':
        for path in sorted(audit.glob('*.terminal')):
            fields = path.read_text().split('\t')
            assert len(fields)==12 and fields[0]=='1' and fields[4]=='true'
            assert path.name=='target-'+str(uuid.UUID(fields[1]))+'.terminal'
            assert fields[3]==target_pid
            expected_counts = [0,0,1,0,0,0,0] if missing and fields[2]==PREFIX+'SuiteD' else [3,0,0,0,0,0,0]
            assert [int(value) for value in fields[5:]]==expected_counts
            terminals.append(fields[2])
        assert sorted(terminals)==[PREFIX+s for s in ['SuiteC','SuiteD','SuiteE']]
        output = [line.split('\t') for line in (audit/'host.output').read_text().splitlines()]
        assert output==[[PREFIX+s,*(['0','0','1'] if missing and s=='SuiteD' else ['3','0','0'])] for s in ['SuiteC','SuiteD','SuiteE']]
    if (audit/'host.parent').is_file():
        parent = (audit/'host.parent').read_text(); parents.add(parent)
        assert (target_pid!=parent)==row['fork']
    if row['fork']:
        assert target_pid not in children; children.add(target_pid)
        try: os.kill(int(target_pid),0); raise AssertionError('Target survived command')
        except ProcessLookupError: pass
    return dict(case=row['name'],bodies=len(bodies),xmlCases=len(passed)+len(errors),errors=len(errors),targetPid=target_pid,terminalSuites=terminals)

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--artifact-version',required=True)
    parser.add_argument('--scala-version',nargs='+',choices=['3.9.0','2.13.18'],required=True)
    parser.add_argument('--expected-missing-report',choices=['absent','error'],required=True)
    parser.add_argument('--evidence-dir',type=Path,required=True)
    args = parser.parse_args()
    run_delivery(Path(__file__), ROOT, args, CONTROL, FRAMEWORK, SETTINGS, verify_case, 'expectedMissingReport', args.expected_missing_report, TIMEOUT_SECONDS)

if __name__=='__main__': main()
