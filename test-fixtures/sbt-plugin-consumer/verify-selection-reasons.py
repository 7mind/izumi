#!/usr/bin/env python3
import hashlib, json, subprocess, traceback
from pathlib import Path


import sys
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from fixture_harness import load_module, run_process, consumer_parser

def main():
    parser = consumer_parser()
    parser.add_argument('--scala-version',choices=['3.9.0','2.13.18'],required=True)
    args=parser.parse_args();root=args.repo_root.resolve();out=args.evidence_dir.resolve();out.mkdir()
    matrix_path=root/'test-fixtures/sbt-plugin-consumer/verify-matrix.py'
    matrix = load_module('matrix', matrix_path)
    original=root/'test-fixtures/host-sharing-consumer';build=out/'build';build.mkdir()
    paths=[original/'build.sbt',*sorted((original/'src').rglob('*.scala'))]
    for path in paths:
        destination=build/path.relative_to(original);destination.parent.mkdir(parents=True,exist_ok=True)
        text=path.read_text()
        if path.name=='build.sbt':
            start=text.index('Test / testFrameworks :=');end=text.index('Test / javaOptions +=',start)
            text=text[:start]+text[end:]+matrix.CONTROLS+matrix.SBT2_DIGEST_CONTROL
        destination.write_text(text)
    (build/'project').mkdir()
    (build/'project/build.properties').write_text('sbt.version=2.0.9\n')
    (build/'project/plugins.sbt').write_text('addSbtPlugin("io.7mind.izumi" % "sbt-distage-testkit" % '+json.dumps(args.artifact_version)+')\n')
    external=out/'external-input.txt';external.write_text('alpha')
    commands=['set Global / localCacheDirectory := file('+json.dumps(str(out/'local-cache'))+')','verifyDistinctStockDigests']
    cases=[]
    foreign='izumi.fixtures.host.ForeignSuite'
    owned='izumi.fixtures.host.SuiteC'
    for fork in [False,True]:
        commands.append('set Test / fork := '+str(fork).lower())
        def case(label,request,labels,acquisitions,reasons):
            name=('fork-' if fork else 'inprocess-')+label
            commands.extend(['prepareFixture '+name,request,('verifyFixture '+name+' '+str(acquisitions)+' '+labels) if labels else 'verifyNoRun '+name])
            cases.append(dict(name=name,request=request,labels=labels.split(),acquisitions=acquisitions,reasons=reasons))
        case('warm','testFull',matrix.ALL_SUITES+' ForeignSuite',1,{})
        case('cached','testQuick *ForeignSuite','',0,{foreign:'cached-success'})
        case('cached-alias','test *ForeignSuite','',0,{foreign:'cached-success'})
        case('user-request','testQuick *ForeignSuite -*ForeignSuite','',0,{foreign:'user-request'})
        case('explicit-user-request','testOnly *ForeignSuite -*ForeignSuite','',0,{foreign:'user-request'})
        commands.append('set Test / testOptions += Tests.Exclude(Seq('+json.dumps(foreign)+'))')
        case('configured-exclusion','testQuick *ForeignSuite','',0,{foreign:'user-configuration'})
        case('full-configured-exclusion','testFull',matrix.ALL_SUITES,1,{foreign:'user-configuration'})
        commands.append('set Test / testOptions ~= (_.filterNot(_.isInstanceOf[Tests.Exclude]))')
        commands.append('set Test / testOptions += Tests.Filter(name => name != '+json.dumps(foreign)+')')
        case('configured-filter','testQuick *ForeignSuite','',0,{foreign:'user-configuration'})
        case('explicit-configured-filter','testOnly *ForeignSuite','',0,{foreign:'user-configuration'})
        commands.append('set Test / testOptions ~= (_.filterNot(_.isInstanceOf[Tests.Filter]))')
        case('explicit','testOnly *ForeignSuite','ForeignSuite',0,{})
        case('multiple-includes','testOnly *SuiteA *ForeignSuite','SuiteA ForeignSuite',0,{})
        case('owned-user-request','testQuick *Suite* -*SuiteC','SuiteA SuiteB SuiteD SuiteE',1,{owned:'user-request',foreign:'cached-success'})
        commands.append('set Test / testOptions += Tests.Filters(Seq(name => name.endsWith("SuiteA"), name => name.endsWith("SuiteB")))')
        case('configured-ordered-filters','testFull','SuiteA SuiteB',0,{foreign:'user-configuration',owned:'user-configuration'})
        commands.append('set Test / testOptions ~= (_.filterNot(_.isInstanceOf[Tests.Filters]))')
    inputs=[dict(path=str(p),sha256=hashlib.sha256(p.read_bytes()).hexdigest()) for p in [*paths,Path(__file__).resolve(),matrix_path]]
    generated=[dict(path=str(p),sha256=hashlib.sha256(p.read_bytes()).hexdigest()) for p in sorted(build.rglob('*')) if p.is_file()]
    argv=['direnv','exec',str(root),'sh','-c','exec sbt --server --sbt-version 2.0.9 -java-home "$JDK21" -batch -J-Xmx6G "$@"','selection-reasons','-Dizumi.fixture.scala-version='+args.scala_version,'-Dizumi.fixture.version='+args.artifact_version,'-Dizumi.fixture.audit-root='+str(build/'target/body-audit'),'-Dizumi.fixture.captures='+str(out/'cases'),'-Dizumi.fixture.external-input='+str(external),*commands]
    (out/'command.json').write_text(json.dumps(dict(argv=argv,cwd=str(build),head=subprocess.check_output(['git','rev-parse','HEAD'],cwd=root,text=True).strip(),inputs=inputs,generated=generated,cases=cases),indent=2)+'\n')
    with (out/'run.log').open('x') as log:
        code = run_process(argv, build, log, matrix.LANE_TIMEOUT_SECONDS, matrix.SHUTDOWN_GRACE_SECONDS)
    failures=[];checks=[];raw=(out/'run.log').read_text()
    if code:failures.append('SBT failed: inspect run.log')
    else:
        for row in cases:
            try:
                marker='TARGET_BOOTSTRAP_PREPARED case='+row['name']+'\n'
                start=raw.index(marker)+len(marker);end=raw.find('TARGET_BOOTSTRAP_PREPARED case=',start)
                segment=raw[start:end if end>=0 else len(raw)]
                for suite,reason in row['reasons'].items():
                    required='DISTAGE_SELECTION_DECISION suite='+suite+' decision=exclude reason='+reason
                    assert required in segment,('SELECTION_REASON_MISSING',row['name'],suite,reason)
                    for other in ('cached-success','user-request','user-configuration','inherited-filter'):
                        if other!=reason:
                            assert 'DISTAGE_SELECTION_DECISION suite='+suite+' decision=exclude reason='+other not in segment,('SELECTION_REASON_CONFLICT',row['name'],suite,reason,other)
                for label in row['labels']:
                    assert 'DISTAGE_SELECTION_DECISION suite=izumi.fixtures.host.'+label+' decision=exclude' not in segment,('INCLUDED_SUITE_REPORTED_EXCLUDED',row['name'],label)
                checks.append(row)
            except (AssertionError,ValueError,OSError) as cause:failures.append(repr(cause)+'\n'+traceback.format_exc())
    changed=[r['path'] for r in inputs+generated if hashlib.sha256(Path(r['path']).read_bytes()).hexdigest()!=r['sha256']]
    result=dict(exit=int(bool(code) or bool(failures) or bool(changed)),actualExit=code,scala=args.scala_version,checks=checks,failures=failures,inputsChanged=changed)
    (out/'completion.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(dict(exit=result['exit'],actualExit=code,checks=len(checks),failures=failures)),flush=True)
    raise SystemExit(result['exit'])


if __name__=='__main__':main()
