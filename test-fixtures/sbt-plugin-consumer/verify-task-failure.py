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
    project='taskFailureConsumer',
    framework='ThrowingTaskFramework',
    command='TaskFailure',
    fault_mode='task-error',
    fault_flag='throw.flag',
    label='task-failure',
    run_label='task-failure',
    rejection='TASK_FAILURE_FALSE_SUCCESS',
)
FRAMEWORK = delivery_framework(CONTROL, r'''        original.tasks(definitions.sortBy(_.fullyQualifiedName())).map { task => new Task {
          override def taskDef(): TaskDef = task.taskDef()
          override def tags(): Array[String] = task.tags()
          override def execute(handler: EventHandler, loggers: Array[Logger]): Array[Task] = {
            val children = task.execute(handler,loggers)
            if (Files.isRegularFile(audit.resolve("throw.flag")) && task.taskDef().fullyQualifiedName() == "izumi.fixtures.host.SuiteC") {
              val data = ProcessHandle.current().pid().toString + "\tSDK_TASK_THROW_AFTER_COMPLETE_BODY_GROUP"
              val _ = Files.write(audit.resolve("task.throw"),data.getBytes(StandardCharsets.UTF_8),StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)
              throw new LinkageError("SDK_TASK_THROW_AFTER_COMPLETE_BODY_GROUP")
            }
            children
          }
        }}''')
SETTINGS = delivery_settings(CONTROL)

def verify_case(lane, row, args, resources, parents, children):
    case = lane/'cases'/row['name']; audit = case/'audit'; missing = row['mode']=='task-error'
    labels = ['SuiteC','SuiteD','SuiteE']
    bodies = [p.read_text().split('\t') for p in audit.glob('*.body')]
    assert sorted((r[0],r[1]) for r in bodies)==sorted((PREFIX+s,str(i)) for s in labels for i in range(1,4))
    acquired = [p.read_text() for p in audit.glob('*.acquire')]; released = [p.read_text() for p in audit.glob('*.release')]
    assert len(acquired)==1 and acquired==released and not resources.intersection(acquired)
    resources.update(acquired)
    selected = (audit/'target.selected').read_text().splitlines(); target_pid = selected[0]
    assert selected[1:]==[PREFIX+s for s in labels]
    reports = [ElementTree.parse(p) for p in (case/'test-reports').glob('*.xml')]
    passed = [(n.attrib['classname'],n.attrib['name']) for x in reports for n in x.findall('.//testcase') if n.find('error') is None]
    errors = [n for x in reports for n in x.findall('.//testcase') if n.find('error') is not None]
    assert all(not x.findall('.//failure') and not x.findall('.//skipped') for x in reports)
    expected_labels = sorted({name.removeprefix(PREFIX) for name,_ in passed}) if missing and args.expected_task_report=='absent' else labels
    if missing and args.expected_task_report=='absent': assert set(expected_labels)<= {'SuiteD','SuiteE'}
    assert sorted(passed)==sorted((PREFIX+s,'equal display name should '+leaf) for s in expected_labels for leaf in ['first','second','third'])
    if missing:
        assert (audit/'host.rejected').is_file() and (audit/'task.throw').read_text()==target_pid+'\tSDK_TASK_THROW_AFTER_COMPLETE_BODY_GROUP'
        if args.expected_task_report=='absent':
            assert not errors and not (audit/'host.output').exists()
            assert 'SDK_TASK_THROW_AFTER_COMPLETE_BODY_GROUP' in (audit/'host.rejected').read_text()
        else:
            assert len(errors)==1 and errors[0].attrib['classname']==PREFIX+'SuiteC'
            assert 'SDK_TASK_THROW_AFTER_COMPLETE_BODY_GROUP' in ''.join(errors[0].itertext())
    else: assert not errors and not (audit/'host.rejected').exists() and not (audit/'task.throw').exists()
    terminals = []
    if args.expected_task_report=='error' or not missing:
        for path in sorted(audit.glob('*.terminal')):
            fields = path.read_text().split('\t')
            assert len(fields)==12 and fields[0]=='1' and path.name=='target-'+str(uuid.UUID(fields[1]))+'.terminal'
            assert fields[3]==target_pid
            failed = missing and fields[2]==PREFIX+'SuiteC'
            assert fields[4]==('false' if failed else 'true')
            assert [int(value) for value in fields[5:]]==[3,0,1 if failed else 0,0,0,0,0]
            terminals.append(fields[2])
        assert sorted(terminals)==[PREFIX+s for s in labels]
        output = [line.split('\t') for line in (audit/'host.output').read_text().splitlines()]
        assert output==[[PREFIX+s,'3','0','1' if missing and s=='SuiteC' else '0'] for s in labels]
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
    parser.add_argument('--expected-task-report',choices=['absent','error'],required=True)
    parser.add_argument('--evidence-dir',type=Path,required=True)
    args = parser.parse_args()
    run_delivery(Path(__file__), ROOT, args, CONTROL, FRAMEWORK, SETTINGS, verify_case, 'expectedTaskReport', args.expected_task_report, TIMEOUT_SECONDS)

if __name__=='__main__': main()
