#!/usr/bin/env python3
from pathlib import Path
from xml.etree import ElementTree
import argparse
import hashlib
import json
import os
import shutil
import uuid

import sys
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from fixture_harness import load_module, run_process

ROOT = Path(__file__).resolve().parents[2]
TIMEOUT_SECONDS = 600
PREFIX = 'izumi.fixtures.host.'

fixture = load_module('task_delivery', Path(__file__).with_name('verify-task-failure.py'))
FRAMEWORK = fixture.FRAMEWORK.replace('ThrowingTaskFramework', 'OmittingFramework')
start = FRAMEWORK.index('        original.tasks(definitions.sortBy(_.fullyQualifiedName())).map { task => new Task {')
end = FRAMEWORK.index('        }}', start) + len('        }}')
FRAMEWORK = FRAMEWORK[:start] + r'''        val admitted = if (Files.isRegularFile(audit.resolve("omit.flag"))) definitions.filterNot(_.fullyQualifiedName() == "izumi.fixtures.host.SuiteD") else definitions
        original.tasks(admitted).map { task => new Task {
          override def taskDef(): TaskDef = task.taskDef()
          override def tags(): Array[String] = task.tags()
          override def execute(handler: EventHandler, loggers: Array[Logger]): Array[Task] = task.execute(handler,loggers)
        }}''' + FRAMEWORK[end:]
SETTINGS = fixture.SETTINGS
for before, after in [
    ('taskFailureConsumer', 'missingSuiteConsumer'),
    ('ThrowingTaskFramework', 'OmittingFramework'),
    ('TaskFailure', 'MissingSuite'),
    ('one owned task-failure control', 'one owned omission control'),
    ('"task-error"', '"missing"'),
    ('Missing task-failure mode', 'Missing omission mode'),
    ('"throw.flag"', '"omit.flag"'),
    ('TASK_FAILURE_FALSE_SUCCESS', 'TARGET_OMISSION_FALSE_SUCCESS'),
    ('Missing task-failure case', 'Missing omission case'),
    ('Task-failure capture must be new', 'Omission capture must be new'),
    ('Task-failure command root survived', 'Omission command root survived'),
]:
    SETTINGS = SETTINGS.replace(before, after)

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--artifact-version',required=True)
    parser.add_argument('--scala-version',nargs='+',choices=['3.9.0','2.13.18'],required=True)
    parser.add_argument('--expected-missing-report',choices=['absent','error'],required=True)
    parser.add_argument('--evidence-dir',type=Path,required=True)
    args = parser.parse_args(); out = args.evidence_dir.resolve(); out.mkdir()
    shutil.copy2(__file__,out/'driver.py')
    fixture = ROOT/'test-fixtures/host-sharing-consumer'; outcomes = []
    for scala in args.scala_version:
        lane = out/('scala'+scala); build = lane/'build'; (build/'project').mkdir(parents=True)
        for source in sorted((fixture/'src').rglob('*.scala')):
            target = build/source.relative_to(fixture); target.parent.mkdir(parents=True,exist_ok=True); shutil.copy2(source,target)
        (build/'src/test/scala/izumi/fixtures/host/OmittingFramework.scala').write_text(FRAMEWORK)
        settings = (fixture/'build.sbt').read_text()
        start = settings.index('Test / testFrameworks :=')
        end = settings.index('Test / javaOptions +=',start)
        (build/'build.sbt').write_text(settings[:start]+settings[end:]+SETTINGS)
        (build/'project/build.properties').write_text('sbt.version=2.0.9\n')
        (build/'project/plugins.sbt').write_text('addSbtPlugin("io.7mind.izumi" % "sbt-distage-testkit" % "'+args.artifact_version+'")\n')
        commands = ['set Global / localCacheDirectory := file("'+str(lane/'local-cache')+'")']; cases = []
        for fork in [False,True]:
            commands += ['set Test / fork := '+str(fork).lower()]
            for mode in ['normal','missing','recovery']:
                name = ('fork-' if fork else 'inprocess-')+mode; cases.append(dict(name=name,fork=fork,mode=mode))
                request = 'observeMissingSuite' if mode=='missing' else 'testOnly *SuiteC *SuiteD *SuiteE'
                commands += ['prepareMissingSuite '+mode,request,'captureMissingSuite '+name]
        inputs = [dict(path=str(p),sha256=hashlib.sha256(p.read_bytes()).hexdigest()) for p in sorted(build.rglob('*')) if p.is_file()]
        argv = ['direnv','exec',str(ROOT),'sh','-c','exec sbt --server --sbt-version 2.0.9 -java-home "$JDK21" -batch -J-Xmx6G "$@"','missing-suite','-Dizumi.fixture.scala-version='+scala,'-Dizumi.fixture.version='+args.artifact_version,'-Dizumi.fixture.audit-root='+str(build/'target/body-audit'),'-Dizumi.fixture.captures='+str(lane/'cases'),*commands]
        (lane/'commands.json').write_text(json.dumps(dict(cwd=str(build),argv=argv,inputs=inputs,cases=cases),indent=2)+'\n')
        print('MISSING_SUITE_BATCH_START '+scala,flush=True)
        with (lane/'run.log').open('x') as log:
            code = run_process(argv, build, log, TIMEOUT_SECONDS, 10)
        failures = []; checks = []; resources = set(); parents = set(); children = set()
        if code: failures.append('SBT process failed: inspect run.log')
        else:
            for row in cases:
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
                checks.append(dict(case=row['name'],bodies=len(bodies),xmlCases=len(passed)+len(errors),errors=len(errors),targetPid=target_pid,terminalSuites=terminals))
            assert len(parents)==1
        for row in inputs: assert hashlib.sha256(Path(row['path']).read_bytes()).hexdigest()==row['sha256']
        result = dict(scala=scala,actualExit=code,expectedMissingReport=args.expected_missing_report,failures=failures,checks=checks)
        (lane/'completion.json').write_text(json.dumps(result,indent=2)+'\n'); outcomes.append(result); print(json.dumps(result),flush=True)
        if code: break
    result = dict(exit=0 if len(outcomes)==len(args.scala_version) and all(not r['actualExit'] for r in outcomes) else 1,outcomes=outcomes)
    (out/'completion.json').write_text(json.dumps(result,indent=2)+'\n'); raise SystemExit(result['exit'])

if __name__=='__main__': main()
