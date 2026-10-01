#!/usr/bin/env python3
"""Run real SBT fixture commands; assert execution bodies and fresh JUnit reports."""
import argparse, json, re, subprocess, xml.etree.ElementTree as ET
from pathlib import Path
BASE = Path(__file__).resolve().parent

def run(version, label, commands, bodies, applications, reports=None, foreign=0):
    (BASE / 'logs').mkdir(parents=True, exist_ok=True)
    fixture = BASE / ('fixture' if version == 1 else 'fixture-sbt2')
    ledger = fixture / 'target/audit.log'
    if ledger.exists(): ledger.unlink()
    for xml in fixture.rglob('TEST-*.xml'): xml.unlink()
    cmd = ['sbt', '-batch', '-Dsbt.supershell=false', '; '.join(['session clear'] + commands)]
    completed = subprocess.run(cmd, cwd=fixture, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, timeout=180)
    (BASE / f'logs/sbt{version}-{label}.log').write_text(completed.stdout)
    lines = ledger.read_text().splitlines() if ledger.exists() else []
    (BASE / f'logs/sbt{version}-{label}.audit').write_text('\n'.join(lines)+'\n')
    actual_bodies = [line for line in lines if line.startswith('BODY ')]
    actual_foreign = [line for line in lines if line.startswith('FOREIGN_BODY ')]
    apps = [line for line in lines if line.startswith('APP HOST ')]
    xml_reports = {}
    for xml in fixture.rglob('TEST-*.xml'):
        suite = ET.parse(xml).getroot()
        xml_reports[suite.attrib['name']] = {'tests':int(suite.attrib['tests']), 'cases': [c.attrib['name'] for c in suite.findall('testcase')], 'failures':int(suite.attrib['failures']), 'errors':int(suite.attrib['errors'])}
    summary = {'version': version, 'label':label, 'command':cmd, 'exit': completed.returncode, 'bodies':actual_bodies, 'host_applications':apps, 'foreign_bodies':actual_foreign, 'reports':xml_reports}
    (BASE / f'logs/sbt{version}-{label}.json').write_text(json.dumps(summary,indent=2)+'\n')
    assert completed.returncode == 0, summary
    assert len(actual_bodies) == bodies, summary
    assert len(apps) == applications, summary
    assert lines.count('ACQUIRE HOST') == lines.count('RELEASE HOST') == applications, summary
    assert len(actual_foreign) == foreign, summary
    if reports is not None:
        expected_pairs = {f'BODY {name} test{i}' for name, count in reports.items() if name.startswith('spike.Suite') for i in range(1,count+1)}
        assert set(actual_bodies) == expected_pairs, summary
        if len(expected_pairs) and bodies % len(expected_pairs) == 0:
            factor = bodies // len(expected_pairs)
            assert all(actual_bodies.count(pair) == factor for pair in expected_pairs), summary
        assert {n:v['tests'] for n,v in xml_reports.items()} == reports, summary
        for name, count in reports.items():
            assert xml_reports[name]['cases'] == [f'test{i}' for i in range(1,count+1)], summary
            assert xml_reports[name]['failures'] == xml_reports[name]['errors'] == 0, summary
    if label in ['partial-conservative','fork-partial-safe']:
        assert actual_bodies == ['BODY spike.SuiteA test1','BODY spike.SuiteA test1','BODY spike.SuiteA test2','BODY spike.SuiteA test3'], summary
    print(f'PASS sbt{version} {label}: {bodies} bodies, {applications} host apps, reports={ {n:v["tests"] for n,v in xml_reports.items()} }', flush=True)
    return summary

if __name__ == '__main__':
    p=argparse.ArgumentParser(); p.add_argument('version',type=int); a=p.parse_args(); v=a.version
    names=['spike.Suite'+c for c in 'ABCDE']
    run(v,'explicit-repeat',['testOnly spike.SuiteA spike.SuiteB','testOnly spike.SuiteA spike.SuiteB'],12,2,dict.fromkeys(names[:2],3))
    run(v,'exclusion',['testOnly spike.Suite* -spike.SuiteC'],12,1,dict.fromkeys([n for n in names if n!='spike.SuiteC'],3))
    run(v,'wildcard',['testOnly spike.Suite*'],15,1,dict.fromkeys(names,3))
    run(v,'full',['test' if v == 1 else 'testFull'],15,1,{**dict.fromkeys(names,3),'spike.ForeignSuite':3},3)
    run(v,'incremental-repeat',['testQuick' if v == 1 else 'test'],0,0,{})
    fixture = BASE / ('fixture' if v == 1 else 'fixture-sbt2')
    suite = fixture / 'src/test/java/spike/SuiteA.java'
    source = suite.read_text()
    match = re.search(r'REVISION = (\d+)', source)
    if match: source = source.replace(match.group(0), 'REVISION = ' + str(int(match.group(1))+1))
    else: source = source.replace('extends Spec {', 'extends Spec { public static final int REVISION = 1;')
    suite.write_text(source)
    run(v,'partial',['testOnly spike.SuiteA -- --one'],1,1,{'spike.SuiteA':1})
    run(v,'after-partial',['testQuick spike.SuiteA' if v == 1 else 'test spike.SuiteA'],0 if v == 1 else 3,0 if v == 1 else 1,{} if v == 1 else {'spike.SuiteA':3})
    run(v,'changed-arguments',['testOnly spike.SuiteA -- --different'],3,1,{'spike.SuiteA':3})
    run(v,'fork',['set Test / fork := true','testOnly spike.SuiteA spike.SuiteB'],6,0,dict.fromkeys(names[:2],3))
