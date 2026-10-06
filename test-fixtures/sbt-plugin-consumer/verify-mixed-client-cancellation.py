#!/usr/bin/env python3
import argparse,hashlib,importlib.util,json,os,signal,subprocess,tempfile,time,traceback
from pathlib import Path
from xml.etree import ElementTree


FOREIGN = r'''package fixture
import sbt.testing.{Event, EventHandler, Fingerprint, Framework, Logger, OptionalThrowable, Runner, Selector, Status, SubclassFingerprint, Task, TaskDef, TestSelector}
trait ForeignMarker
final class ForeignSuite extends ForeignMarker
final class ForeignFramework extends Framework {
  private val marker = new SubclassFingerprint {
    override def isModule(): Boolean = false
    override def superclassName(): String = "fixture.ForeignMarker"
    override def requireNoArgConstructor(): Boolean = true
  }
  override def name(): String = "Original foreign framework"
  override def fingerprints(): Array[Fingerprint] = Array(marker)
  override def runner(arguments: Array[String], remote: Array[String], loader: ClassLoader): Runner = new Runner {
    override def args(): Array[String] = arguments
    override def remoteArgs(): Array[String] = remote
    override def done(): String = ""
    override def tasks(definitions: Array[TaskDef]): Array[Task] = definitions.map { definition => new Task {
      override def taskDef(): TaskDef = definition
      override def tags(): Array[String] = Array.empty
      override def execute(handler: EventHandler, loggers: Array[Logger]): Array[Task] = {
        (1 to 3).foreach { index =>
          Audit.write(index.toString + ".foreign-body", Vector(definition.fullyQualifiedName(), index.toString, java.lang.ProcessHandle.current().pid().toString).mkString("\t"))
          val event = new Event {
            override def fullyQualifiedName(): String = definition.fullyQualifiedName()
            override def fingerprint(): Fingerprint = definition.fingerprint()
            override def selector(): Selector = new TestSelector("foreign-" + index)
            override def status(): Status = Status.Success
            override def throwable(): OptionalThrowable = new OptionalThrowable
            override def duration(): Long = index * 10L
          }
          handler.handle(event)
        }
        Array.empty
      }
    } }
  }
}
'''

LISTENER = r'''
Test / testFrameworks := new TestFramework("fixture.ForeignFramework") +: (Test / testFrameworks).value
Test / testOptions += {
  val audit = baseDirectory.value / "audit"
  Tests.Listeners(Seq(new TestsListener {
    override def doInit(): Unit = ()
    override def doComplete(result: TestResult): Unit = ()
    override def startGroup(name: String): Unit = ()
    override def endGroup(name: String, cause: Throwable): Unit = ()
    override def endGroup(name: String, result: TestResult): Unit = ()
    override def testEvent(event: TestEvent): Unit = event.detail.filter(_.fullyQualifiedName() == "fixture.ForeignSuite").foreach { value =>
      if ((audit / "host-command-returned").isFile) IO.write(audit / "foreign.late", "late")
      val selector = value.selector().asInstanceOf[sbt.testing.TestSelector].testName()
      val fingerprint = value.fingerprint().asInstanceOf[sbt.testing.SubclassFingerprint]
      val fields = Vector(value.fullyQualifiedName(), selector, value.status().toString, value.duration().toString, value.throwable().isDefined.toString, fingerprint.superclassName(), fingerprint.isModule().toString, fingerprint.requireNoArgConstructor().toString)
      IO.write(audit / (java.util.UUID.randomUUID().toString + ".foreign-event"), fields.mkString("\t"))
    }
  }))
}
'''


def module(name,path):
    spec=importlib.util.spec_from_file_location(name,path)
    result=importlib.util.module_from_spec(spec);spec.loader.exec_module(result)
    return result


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--repo-root',type=Path,required=True)
    parser.add_argument('--evidence-dir',type=Path,required=True)
    parser.add_argument('--artifact-version',required=True)
    parser.add_argument('--scala-version',choices=['3.9.0','2.13.18'],required=True)
    args=parser.parse_args();root=args.repo_root.resolve();out=args.evidence_dir.resolve();out.mkdir()
    api_path=root/'test-fixtures/sbt-plugin-consumer/verify-client-cancellation.py'
    fixture_path=root/'test-fixtures/sbt-plugin-consumer/verify-task-cancellation.py'
    api=module('client_api',api_path);fixture=module('fixture',fixture_path)
    build=out/'build';(build/'project').mkdir(parents=True)
    source=build/'src/test/scala';source.mkdir(parents=True)
    (source/'Suites.scala').write_text(fixture.SOURCE.replace('@AUDIT@',json.dumps(str(build/'audit')))+'\n'+'\n'.join(f'final class {s} extends CancellationSuite' for s in fixture.SUITES)+'\n')
    (source/'Plugin.scala').write_text(fixture.PLUGIN)
    (source/'Foreign.scala').write_text(FOREIGN)
    settings=fixture.SETTINGS[:fixture.SETTINGS.index('val proxyFramework')]+fixture.SETTINGS[fixture.SETTINGS.index('lazy val prepareCancellation'):]
    settings=settings.replace('@SCALA@',json.dumps(args.scala_version)).replace('@VERSION@',json.dumps(args.artifact_version)).replace('@CAPTURES@',json.dumps(str(out/'cases')))
    (build/'build.sbt').write_text(settings+LISTENER)
    (build/'project/build.properties').write_text('sbt.version=2.0.9\n')
    (build/'project/plugins.sbt').write_text('addSbtPlugin("io.7mind.izumi" % "sbt-distage-testkit" % '+json.dumps(args.artifact_version)+')\n')
    paths=[p for p in sorted(build.rglob('*')) if p.is_file()]+[Path(__file__).resolve(),api_path,fixture_path]
    inputs=[dict(path=str(p),sha256=hashlib.sha256(p.read_bytes()).hexdigest()) for p in paths]
    argv=['direnv','exec',str(root),'sh','-c','exec sbt --server --sbt-version 2.0.9 -java-home "$JDK21" -batch -J-Xmx6G "$@"','mixed-cancel','--detach-stdio','startServer','shell']
    (out/'command.json').write_text(json.dumps(dict(argv=argv,cwd=str(build),head=subprocess.check_output(['git','rev-parse','HEAD'],cwd=root,text=True).strip(),inputs=inputs),indent=2)+'\n')
    client=None;checks=[];failures=[];actual=None;runs=set();resources=set();audit=build/'audit'
    log=(out/'server.log').open('x');server=tempfile.TemporaryDirectory(prefix='distage-rpc-',dir=root/'target')
    process=subprocess.Popen(argv,cwd=build,stdin=subprocess.PIPE,stdout=log,stderr=subprocess.STDOUT,start_new_session=True,env=dict(os.environ,SBT_GLOBAL_SERVER_DIR=server.name))
    try:
        active=build/'project/target/active.json';api.wait(active.exists,process,'MIXED_CANCEL_NO_SERVER',api.WAIT_SECONDS)
        client=api.Client(json.loads(active.read_text()))
        initialized=client.await_response(client.send('initialize',dict(processId=os.getpid(),rootUri=build.as_uri(),capabilities={},initializationOptions=dict(skipAnalysis=True,canWork=False))))
        assert 'result' in initialized
        for fork in [False,True]:
            client.successful('set Test / fork := '+str(fork).lower())
            for mode in ['normal','cancel','recovery']:
                client.successful('prepareCancellation '+mode)
                identity=client.send('sbt/exec',dict(commandLine='testOnly fixture.Suite* fixture.ForeignSuite'))
                name=('fork-' if fork else 'inprocess-')+mode
                row=dict(name=name,mode=mode,fork=fork,request='testOnly fixture.Suite* fixture.ForeignSuite',execId=identity)
                if mode=='cancel':
                    api.wait(lambda:len(list(audit.glob('*.body')))==1 and len(list(audit.glob('*.foreign-event')))==3,process,'MIXED_CANCEL_PRECONDITION_MISSING',api.WAIT_SECONDS)
                    cancelled=client.send('sbt/cancelRequest',dict(id=api.CANCEL_CURRENT_CHANNEL))
                    api.wait(lambda:bool(list(audit.glob('*.release-entered'))),process,'MIXED_CANCEL_FINALIZER_NOT_ENTERED',api.RELEASE_WAIT_SECONDS)
                    time.sleep(api.RELEASE_HOLD_SECONDS)
                    assert not list(audit.glob('*.release')) and identity not in client.responses,'MIXED_CANCEL_COMMAND_RETURNED_BEFORE_RELEASE'
                    (audit/'release.observed-held').write_text('held');(audit/'allow-release').write_text('release')
                    row['cancelResponse']=client.await_response(cancelled)
                row['response']=client.await_response(identity)
                (audit/'host-command-returned').write_text('returned')
                client.successful('captureCancellation '+name)
                capture=out/'cases'/name;check=api.verify(capture,row,runs,resources)
                foreign=[tuple(p.read_text().split('\t')) for p in (capture/'audit').glob('*.foreign-body')]
                assert {(b[0],b[1]) for b in foreign}=={('fixture.ForeignSuite',str(i)) for i in range(1,4)} and len(foreign)==3,'MIXED_CANCEL_FOREIGN_EXECUTION_CHANGED'
                assert all(b[2] in check['bodyPids'] for b in foreign),'MIXED_CANCEL_FOREIGN_TARGET_CHANGED'
                events=[tuple(p.read_text().split('\t')) for p in (capture/'audit').glob('*.foreign-event')]
                expected={('fixture.ForeignSuite','foreign-'+str(i),'Success',str(i*10),'false','fixture.ForeignMarker','false','true') for i in range(1,4)}
                assert len(events)==3 and set(events)==expected,'MIXED_CANCEL_FOREIGN_EVENT_PAYLOAD_CHANGED'
                nodes=[n for p in (capture/'test-reports').glob('*.xml') for n in ElementTree.parse(p).findall('.//testcase') if n.get('classname')=='fixture.ForeignSuite']
                assert len(nodes)==3 and {n.get('name') for n in nodes}=={'foreign-'+str(i) for i in range(1,4)} and all(n.find('error') is None and n.find('failure') is None and n.find('skipped') is None for n in nodes),'MIXED_CANCEL_FOREIGN_XML_CHANGED'
                assert not (capture/'audit/foreign.late').exists(),'MIXED_CANCEL_LATE_FOREIGN_EVENT'
                check.update(foreignBodies=3,foreignEvents=3,foreignXml=3);checks.append(check)
                print('MIXED_CANCEL_CASE_OK '+name,flush=True)
        client.send('sbt/exec',dict(commandLine='shutdown'));actual=process.wait(timeout=30);assert actual==0
    except BaseException as cause:
        failures.append(repr(cause)+'\n'+traceback.format_exc());print(failures[-1],flush=True)
        if audit.is_dir():(audit/'allow-release').write_text('release')
        actual=process.poll()
        if client is not None:
            try:
                client.send('sbt/cancelRequest',dict(id=api.CANCEL_CURRENT_CHANNEL))
                client.send('sbt/exec',dict(commandLine='shutdown'));actual=process.wait(timeout=30)
            except BaseException as cleanup:failures.append('Cleanup: '+repr(cleanup))
    finally:
        if audit.is_dir() and not (audit/'allow-release').exists():(audit/'allow-release').write_text('release')
        if client is not None:(out/'rpc.json').write_text(json.dumps(client.frames,indent=2)+'\n');client.close()
        if process.poll() is None:
            os.killpg(process.pid,signal.SIGTERM)
            try:process.wait(timeout=10)
            except subprocess.TimeoutExpired:os.killpg(process.pid,signal.SIGKILL);process.wait()
        actual=process.returncode
        log.close();server.cleanup()
    changed=[r['path'] for r in inputs if hashlib.sha256(Path(r['path']).read_bytes()).hexdigest()!=r['sha256']]
    result=dict(exit=int(bool(failures) or bool(changed)),actualExit=actual,scala=args.scala_version,checks=checks,failures=failures,inputsChanged=changed)
    (out/'completion.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(dict(exit=result['exit'],checks=len(checks))),flush=True)
    raise SystemExit(result['exit'])


if __name__=='__main__':main()
