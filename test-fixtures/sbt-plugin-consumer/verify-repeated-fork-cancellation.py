#!/usr/bin/env python3
from pathlib import Path
from xml.etree import ElementTree as ET
import hashlib, json, os, subprocess, tempfile, time, traceback
import sys
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from fixture_harness import write_sbt_project, load_module as module, scala_consumer_parser

args = scala_consumer_parser(multiple=False).parse_args()
ROOT=args.repo_root.resolve()
OUT=args.evidence_dir.resolve()
OUT.mkdir()

api=module('client_api',ROOT/'test-fixtures/sbt-plugin-consumer/verify-client-cancellation.py')
fixture=module('fixture',ROOT/'test-fixtures/sbt-plugin-consumer/verify-task-cancellation.py')
build=OUT/'build'
source=build/'src/test/scala';source.mkdir(parents=True)
shared=fixture.SOURCE.replace('@AUDIT@',json.dumps(str(build/'audit')))
assert shared.count('directory.resolve(name)')==1
shared=shared.replace('directory.resolve(name)','directory.resolve(java.lang.ProcessHandle.current().pid().toString + "-" + name)')
(source/'Suites.scala').write_text(shared+'\n'+'\n'.join(f'final class {s} extends CancellationSuite' for s in fixture.SUITES)+'\n')
(source/'Plugin.scala').write_text(fixture.PLUGIN)
settings=fixture.SETTINGS[:fixture.SETTINGS.index('val proxyFramework')]+fixture.SETTINGS[fixture.SETTINGS.index('lazy val prepareCancellation'):]
settings=settings.replace('@SCALA@',json.dumps(args.scala_version)).replace('@VERSION@',json.dumps(args.artifact_version)).replace('@CAPTURES@',json.dumps(str(OUT/'cases')))
settings+=r'''
Test / fork := true
Test / testForkedParallel := true
Global / concurrentRestrictions := Seq(Tags.limitAll(4), Tags.limit(Tags.ForkedTestGroup, 2))
Test / testGrouping := Def.uncached {
  val definitions = (Test / definedTests).value
  val options = (Test / forkOptions).value
  Vector("first", "second").map(name => new Tests.Group(name, definitions, Tests.SubProcess(options), Seq.empty))
}
'''
settings+=r'''
Test / testOptions += {
  val audit = baseDirectory.value / "audit"
  Tests.Listeners(Seq(new TestsListener {
    private def record(stage: String, name: String): Unit = {
      if ((audit / "host-command-returned").isFile) IO.write(audit / "listener.late", stage + "\t" + name)
      IO.write(audit / (java.util.UUID.randomUUID().toString + ".callback"), stage + "\t" + name)
    }
    override def doInit(): Unit = ()
    override def doComplete(result: TestResult): Unit = ()
    override def startGroup(name: String): Unit = record("start", name)
    override def testEvent(event: TestEvent): Unit = {
      val names = event.detail.map(_.fullyQualifiedName()).distinct
      require(names.size == 1, "REPEATED_FORK_EVENT_GROUP_DIFFERS")
      record("events", names.head)
    }
    override def endGroup(name: String, cause: Throwable): Unit = record("error", name)
    override def endGroup(name: String, result: TestResult): Unit = record("end", name)
  }))
}
'''
write_sbt_project(build, settings, '2.0.9', 'addSbtPlugin("io.7mind.izumi" % "sbt-distage-testkit" % '+json.dumps(args.artifact_version)+')\n')
argv=['direnv','exec',str(ROOT),'sh','-c','exec sbt --server --sbt-version 2.0.9 -java-home "$JDK21" -batch -J-Xmx6G "$@"','repeated-fork-cancel','--detach-stdio','startServer','shell']
inputs=[dict(path=str(p),sha256=hashlib.sha256(p.read_bytes()).hexdigest()) for p in ([p for p in sorted(build.rglob('*')) if p.is_file()]+[Path(__file__).resolve(),ROOT/'test-fixtures/sbt-plugin-consumer/verify-client-cancellation.py',ROOT/'test-fixtures/sbt-plugin-consumer/verify-task-cancellation.py'])]
(OUT/'command.json').write_text(json.dumps(dict(argv=argv,cwd=str(build),head=subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),inputs=inputs),indent=2)+'\n')
client=None;checks=[];failures=[];actual=None
log=(OUT/'server.log').open('x');server=tempfile.TemporaryDirectory(prefix='distage-rpc-',dir=ROOT/'target')
process=subprocess.Popen(argv,cwd=build,stdin=subprocess.PIPE,stdout=log,stderr=subprocess.STDOUT,start_new_session=True,env=dict(os.environ,SBT_GLOBAL_SERVER_DIR=server.name))
audit=build/'audit'
try:
    active=build/'project/target/active.json';api.wait(active.exists,process,'REPEATED_CANCEL_NO_SERVER',180)
    client=api.Client(json.loads(active.read_text()))
    initialized=client.await_response(client.send('initialize',dict(processId=os.getpid(),rootUri=build.as_uri(),capabilities={},initializationOptions=dict(skipAnalysis=True,canWork=False))))
    assert 'result' in initialized
    for mode in ('normal','cancel','recovery'):
        client.successful('prepareCancellation '+mode)
        identity=client.send('sbt/exec',dict(commandLine='testOnly fixture.Suite*'))
        row=dict(mode=mode,execId=identity)
        if mode=='cancel':
            api.wait(lambda:len(list(audit.glob('*.body')))==2,process,'REPEATED_CANCEL_BOTH_WORKERS_NOT_ENTERED',180)
            before=[p.read_text().split('\t') for p in audit.glob('*.body')]
            assert len({b[3] for b in before})==2,'REPEATED_CANCEL_WORKERS_NOT_DISTINCT'
            cancelled=client.send('sbt/cancelRequest',dict(id='__CancelAll'))
            api.wait(lambda:len(list(audit.glob('*.release-entered')))==2,process,'REPEATED_CANCEL_BOTH_FINALIZERS_NOT_ENTERED',30)
            time.sleep(.25)
            assert not list(audit.glob('*.release')),'REPEATED_CANCEL_RELEASE_IGNORED_GATE'
            assert identity not in client.responses,'REPEATED_CANCEL_COMMAND_RETURNED_BEFORE_RELEASE'
            (audit/'allow-release').write_text('release')
            row['cancelResponse']=client.await_response(cancelled)
        row['response']=client.await_response(identity)
        (audit/'host-command-returned').write_text('returned')
        client.successful('captureCancellation '+mode)
        capture=OUT/'cases'/mode
        bodies=[p.read_text().split('\t') for p in (capture/'audit').glob('*.body')]
        acquired=[p.read_text() for p in (capture/'audit').glob('*.acquire')];released=[p.read_text() for p in (capture/'audit').glob('*.release')]
        assert len(acquired)==2 and sorted(acquired)==sorted(released),'REPEATED_CANCEL_RESOURCE_COUNT'
        pids={b[3] for b in bodies};assert len(pids)==2 and str(process.pid) not in pids
        outcomes=[]
        for path in (capture/'events').glob('*.jsonl'):
            frames=[json.loads(line)['message'] for line in path.read_text().splitlines()]
            assert frames[-1]['kind']=='completed'
            outcomes.append(frames[-1]['outcome'])
        assert len(outcomes)==2 and all(len(o['results'])==15 for o in outcomes),'REPEATED_CANCEL_TARGET_OUTCOMES_INCOMPLETE'
        nodes=[n for path in (capture/'test-reports').glob('*.xml') for n in ET.parse(path).findall('.//testcase')]
        selected=[n for n in nodes if n.get('name','').startswith('same display name should ')]
        callbacks=[tuple(p.read_text().split('\t')) for p in (capture/'audit').glob('*.callback')]
        expected_callbacks={(stage,'fixture.Suite'+suite) for stage in ('start','events','end') for suite in 'ABCDE'}
        assert len(callbacks)==30 and set(callbacks)==expected_callbacks and all(callbacks.count(value)==2 for value in expected_callbacks),('REPEATED_FORK_CALLBACKS_INCOMPLETE',callbacks)
        assert not (capture/'audit/listener.late').exists(),'REPEATED_FORK_LATE_CALLBACK'
        row.update(callbacks=len(callbacks),bodies=len(bodies),resources=acquired,pids=sorted(pids),outcomes=outcomes,xmlCases=len(nodes),selectedXmlCases=len(selected))
        checks.append(row)
        (OUT/'live-observation.json').write_text(json.dumps(row,indent=2)+'\n')
        assert len(selected)==30,('REPEATED_FORK_CANCEL_XML_INCOMPLETE' if mode=='cancel' else 'REPEATED_FORK_XML_INCOMPLETE',len(selected))
        if mode=='cancel':
            assert 'error' in row['response'] or row['response']['result'].get('exitCode')!=0,'REPEATED_CANCEL_FALSE_SUCCESS'
            assert all(o['cancelled'] and all(r['status']=='cancelled' for r in o['results']) for o in outcomes)
            assert all(n.find('skipped') is not None and n.find('skipped').get('message')=='Cancelled' for n in selected)
        else:
            assert len(bodies)==30 and 'error' not in row['response'] and row['response']['result'].get('exitCode')==0
            assert all(not o['cancelled'] and all(r['status']=='succeeded' for r in o['results']) for o in outcomes)
        print('REPEATED_FORK_CASE_OK '+mode,flush=True)
    client.send('sbt/exec',dict(commandLine='shutdown'));actual=process.wait(timeout=30);assert actual==0
except BaseException as cause:
    failures.append(repr(cause)+'\n'+traceback.format_exc());print(failures[-1],flush=True)
    if audit.is_dir():(audit/'allow-release').write_text('release')
    actual=process.poll()
    if client is not None:
        try:client.send('sbt/exec',dict(commandLine='shutdown'));actual=process.wait(timeout=30)
        except BaseException as cleanup:failures.append('Cleanup: '+repr(cleanup))
finally:
    if audit.is_dir() and not (audit/'allow-release').exists():(audit/'allow-release').write_text('release')
    api.close_server(client, process, OUT, log, server)
changed=[r['path'] for r in inputs if not Path(r['path']).is_file() or hashlib.sha256(Path(r['path']).read_bytes()).hexdigest()!=r['sha256']]
result=dict(exit=int(bool(failures) or bool(changed)),actualExit=actual,scala=args.scala_version,checks=checks,failures=failures,inputsChanged=changed)
(OUT/'completion.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps(dict(exit=result['exit'],checks=len(checks),failures=failures)),flush=True)
raise SystemExit(result['exit'])
