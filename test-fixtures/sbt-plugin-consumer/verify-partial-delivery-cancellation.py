#!/usr/bin/env python3
import argparse, hashlib, json, os, signal, subprocess, tempfile, time, traceback
from pathlib import Path


import sys
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from fixture_harness import load_module

LISTENER = r'''
Test / parallelExecution := false
Test / testOptions += {
  val audit = baseDirectory.value / "audit"
  Tests.Listeners(Seq(new TestsListener {
    private final val HoldSeconds = 30L
    private final val PollMillis = 5L
    private val events = new java.util.concurrent.atomic.AtomicInteger()
    private def record(stage: String, suite: String): Unit = {
      if ((audit / "host-command-returned").isFile) IO.write(audit / "listener.late", stage + "\t" + suite)
      IO.write(audit / (java.util.UUID.randomUUID().toString + ".callback"), stage + "\t" + suite)
    }
    override def doInit(): Unit = ()
    override def startGroup(name: String): Unit = record("start", name)
    override def testEvent(event: TestEvent): Unit = {
      val names = event.detail.map(_.fullyQualifiedName()).distinct
      require(names.size == 1, "PARTIAL_DELIVERY_EVENT_GROUP_DIFFERS")
      record("events", names.head)
      if ((audit / "hold-reporting").isFile && events.incrementAndGet() == 2) {
        require((audit / "listener.first-completed").isFile, "PARTIAL_DELIVERY_FIRST_GROUP_NOT_COMPLETED")
        IO.write(audit / "listener.held", names.head)
        val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(HoldSeconds)
        while (!(audit / "allow-listener").isFile && System.nanoTime() < deadline) {
          try Thread.sleep(PollMillis)
          catch { case _: InterruptedException => IO.write(audit / "listener.interrupted", "interrupted") }
        }
        require((audit / "allow-listener").isFile, "PARTIAL_DELIVERY_RELEASE_NOT_ADMITTED")
        IO.write(audit / "listener.returned", "returned")
      }
    }
    override def endGroup(name: String, cause: Throwable): Unit = record("error", name)
    override def endGroup(name: String, result: TestResult): Unit = {
      record("end", name)
      if (!(audit / "listener.first-completed").isFile) IO.write(audit / "listener.first-completed", name)
    }
    override def doComplete(result: TestResult): Unit = ()
  }))
}
'''





def verify_callbacks(capture):
    audit=capture/'audit'
    records=[tuple(p.read_text().split('\t')) for p in audit.glob('*.callback')]
    expected={(stage,'fixture.Suite'+suite) for stage in ('start','events','end') for suite in 'ABCDE'}
    assert len(records)==len(expected) and set(records)==expected,('PARTIAL_DELIVERY_CALLBACKS_INCOMPLETE',records)
    assert not (audit/'listener.late').exists(),'PARTIAL_DELIVERY_LATE_CALLBACK'
    return len(records)


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--repo-root',type=Path,required=True)
    parser.add_argument('--evidence-dir',type=Path,required=True)
    parser.add_argument('--artifact-version',required=True)
    parser.add_argument('--scala-version',choices=['3.9.0','2.13.18'],required=True)
    parser.add_argument('--execution-mode',choices=['both','inprocess','fork'],required=True)
    args=parser.parse_args();root=args.repo_root.resolve();out=args.evidence_dir.resolve();out.mkdir()
    client_path=root/'test-fixtures/sbt-plugin-consumer/verify-client-cancellation.py'
    fixture_path=root/'test-fixtures/sbt-plugin-consumer/verify-task-cancellation.py'
    api=load_module('client_api',client_path);fixture=load_module('fixture',fixture_path)
    build=out/'build';(build/'project').mkdir(parents=True)
    source=build/'src/test/scala';source.mkdir(parents=True)
    (source/'Suites.scala').write_text(fixture.SOURCE.replace('@AUDIT@',json.dumps(str(build/'audit')))+'\n'+'\n'.join(f'final class {name} extends CancellationSuite' for name in fixture.SUITES)+'\n')
    (source/'Plugin.scala').write_text(fixture.PLUGIN)
    settings=fixture.SETTINGS[:fixture.SETTINGS.index('val proxyFramework')]+fixture.SETTINGS[fixture.SETTINGS.index('lazy val prepareCancellation'):]
    settings=settings.replace('@SCALA@',json.dumps(args.scala_version)).replace('@VERSION@',json.dumps(args.artifact_version)).replace('@CAPTURES@',json.dumps(str(out/'cases')))
    (build/'build.sbt').write_text(settings+LISTENER)
    (build/'project/build.properties').write_text('sbt.version=2.0.9\n')
    (build/'project/plugins.sbt').write_text('addSbtPlugin("io.7mind.izumi" % "sbt-distage-testkit" % '+json.dumps(args.artifact_version)+')\n')
    argv=['direnv','exec',str(root),'sh','-c','exec sbt --server --sbt-version 2.0.9 -java-home "$JDK21" -batch -J-Xmx6G "$@"','partial-delivery-cancel','--detach-stdio','startServer','shell']
    paths=[p for p in sorted(build.rglob('*')) if p.is_file()]+[Path(__file__).resolve(),client_path,fixture_path]
    inputs=[dict(path=str(p),sha256=hashlib.sha256(p.read_bytes()).hexdigest()) for p in paths]
    (out/'command.json').write_text(json.dumps(dict(argv=argv,cwd=str(build),inputs=inputs),indent=2)+'\n')
    client=None;checks=[];failures=[];actual=None;runs=set();resources=set()
    log=(out/'server.log').open('x')
    server=tempfile.TemporaryDirectory(prefix='distage-rpc-',dir=root/'target')
    process=subprocess.Popen(argv,cwd=build,stdin=subprocess.PIPE,stdout=log,stderr=subprocess.STDOUT,start_new_session=True,env=dict(os.environ,SBT_GLOBAL_SERVER_DIR=server.name))
    audit=build/'audit'
    try:
        active=build/'project/target/active.json'
        api.wait(active.exists,process,'PARTIAL_DELIVERY_NO_SERVER',api.WAIT_SECONDS)
        client=api.Client(json.loads(active.read_text()))
        initialized=client.await_response(client.send('initialize',dict(processId=os.getpid(),rootUri=build.as_uri(),capabilities={},initializationOptions=dict(skipAnalysis=True,canWork=False))))
        assert 'result' in initialized,initialized
        modes={'both':[False,True],'inprocess':[False],'fork':[True]}
        for fork in modes[args.execution_mode]:
            client.successful('set Test / fork := '+str(fork).lower())
            client.successful('prepareCancellation normal')
            (audit/'hold-reporting').write_text('hold')
            identity=client.send('sbt/exec',dict(commandLine='testOnly fixture.Suite*'))
            api.wait(lambda:(audit/'listener.held').exists(),process,'PARTIAL_DELIVERY_LISTENER_NOT_HELD',api.WAIT_SECONDS)
            bodies=[p.read_text().split('\t') for p in audit.glob('*.body')]
            acquired=[p.read_text() for p in audit.glob('*.acquire')];released=[p.read_text() for p in audit.glob('*.release')]
            assert len(bodies)==15 and len(acquired)==1 and acquired==released,'PARTIAL_DELIVERY_PRECONDITION_RESOURCES_INCOMPLETE'
            name=('fork-' if fork else 'inprocess-')+'cancel-delivery'
            row=dict(name=name,mode='normal',fork=fork,request='testOnly fixture.Suite*',execId=identity,cancellationStage='host-delivery',firstCompleted=(audit/'listener.first-completed').read_text(),heldSuite=(audit/'listener.held').read_text())
            assert row['firstCompleted']!=row['heldSuite'],'PARTIAL_DELIVERY_GROUPS_NOT_DISTINCT'
            cancelled=client.send('sbt/cancelRequest',dict(id=api.CANCEL_CURRENT_CHANNEL))
            time.sleep(api.RELEASE_HOLD_SECONDS)
            row['returnedWhileHeld']=identity in client.responses
            (out/'live-case.json').write_text(json.dumps(row,indent=2)+'\n')
            assert identity not in client.responses,'PARTIAL_DELIVERY_COMMAND_RETURNED_BEFORE_LISTENER'
            (audit/'allow-listener').write_text('release')
            row['cancelResponse']=client.await_response(cancelled)
            assert row['cancelResponse']['result']['status']=='Task cancelled'
            row['response']=client.await_response(identity)
            assert 'error' in row['response'] or row['response']['result'].get('exitCode')!=0,'PARTIAL_DELIVERY_CANCEL_FALSE_SUCCESS'
            (audit/'host-command-returned').write_text('returned')
            client.successful('captureCancellation '+name)
            assert (out/'cases'/name/'audit/listener.returned').is_file(),'PARTIAL_DELIVERY_LISTENER_NOT_JOINED'
            check=api.verify(out/'cases'/name,row,runs,resources)
            check['callbacks']=verify_callbacks(out/'cases'/name);checks.append(check)
            recovery_name=('fork-' if fork else 'inprocess-')+'recovery'
            client.successful('prepareCancellation recovery')
            recovery=dict(name=recovery_name,mode='recovery',fork=fork,request='testOnly fixture.Suite*')
            recovery['response']=client.command(recovery['request'])
            assert 'error' not in recovery['response'] and recovery['response']['result'].get('exitCode')==0,('PARTIAL_DELIVERY_RECOVERY_FAILED',recovery['response'])
            (audit/'host-command-returned').write_text('returned')
            client.successful('captureCancellation '+recovery_name)
            check=api.verify(out/'cases'/recovery_name,recovery,runs,resources)
            check['callbacks']=verify_callbacks(out/'cases'/recovery_name);checks.append(check)
            print('PARTIAL_DELIVERY_MODE_OK fork='+str(fork).lower(),flush=True)
        client.send('sbt/exec',dict(commandLine='shutdown'))
        actual=process.wait(timeout=30);assert actual==0
    except BaseException as cause:
        failures.append(repr(cause)+'\n'+traceback.format_exc());print(failures[-1],flush=True)
        if audit.is_dir():(audit/'allow-listener').write_text('release')
        actual=process.poll()
        if client is not None:
            try:
                client.send('sbt/exec',dict(commandLine='shutdown'))
                actual=process.wait(timeout=30)
            except BaseException as cleanup:failures.append('Cleanup: '+repr(cleanup))
    finally:
        if audit.is_dir() and not (audit/'allow-listener').exists():(audit/'allow-listener').write_text('release')
        if client is not None:
            (out/'rpc.json').write_text(json.dumps(client.frames,indent=2)+'\n');client.close()
        if process.poll() is None:
            os.killpg(process.pid,signal.SIGTERM)
            try:process.wait(timeout=10)
            except subprocess.TimeoutExpired:os.killpg(process.pid,signal.SIGKILL);process.wait()
        log.close();server.cleanup()
    changed=[row['path'] for row in inputs if not Path(row['path']).is_file() or hashlib.sha256(Path(row['path']).read_bytes()).hexdigest()!=row['sha256']]
    result=dict(exit=int(bool(failures) or bool(changed)),actualExit=actual,scala=args.scala_version,checks=checks,failures=failures,inputsChanged=changed)
    (out/'completion.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(dict(exit=result['exit'],checks=len(checks))),flush=True)
    raise SystemExit(result['exit'])


if __name__=='__main__':main()
