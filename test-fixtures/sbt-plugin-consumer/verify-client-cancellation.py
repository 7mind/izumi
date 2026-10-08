#!/usr/bin/env python3
import hashlib, json, os, socket, subprocess, time, threading, traceback, urllib.parse, tempfile
from pathlib import Path
from typing import TextIO
from xml.etree import ElementTree
import sys
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from fixture_harness import write_sbt_project, load_module, terminate_process, consumer_parser

WAIT_SECONDS=180
RELEASE_WAIT_SECONDS=30
RELEASE_HOLD_SECONDS=.25
CANCEL_CURRENT_CHANNEL="__CancelAll"
SERVER_SHUTDOWN_GRACE_SECONDS=10
class Client:
    def __init__(self,address):
        uri=urllib.parse.urlparse(address['uri'])
        assert uri.scheme=='local'
        self.socket=socket.socket(socket.AF_UNIX)
        self.socket.settimeout(WAIT_SECONDS)
        self.socket.connect(urllib.parse.unquote(uri.path))
        self.frames=[];self.responses={};self.next=0
        self.condition=threading.Condition();self.failure=None;self.running=True
        self.reader=threading.Thread(target=self.receive_loop,name='client-rpc-receiver',daemon=True);self.reader.start()
    def send(self,method,params):
        self.next+=1;identity=str(self.next)
        message=dict(jsonrpc='2.0',id=identity,method=method,params=params)
        payload=json.dumps(message).encode()
        self.socket.sendall(('Content-Length: '+str(len(payload))+'\r\n\r\n').encode()+payload)
        self.frames.append(dict(direction='sent',message=message))
        return identity
    def read(self):
        header=b''
        while not header.endswith(b'\r\n\r\n'):
            part=self.socket.recv(1)
            if not part:raise EOFError('RPC channel ended')
            header+=part
            assert len(header)<4096
        length=int(next(line.split(b':',1)[1] for line in header.split(b'\r\n') if line.lower().startswith(b'content-length:')))
        assert 0<length<16777216
        payload=b''
        while len(payload)<length:
            part=self.socket.recv(length-len(payload))
            if not part:raise EOFError('RPC frame ended')
            payload+=part
        message=json.loads(payload);self.frames.append(dict(direction='received',message=message))
        if 'id' in message:
            with self.condition:
                self.responses[str(message['id'])]=message;self.condition.notify_all()
    def receive_loop(self):
        try:
            while self.running:self.read()
        except BaseException as cause:
            with self.condition:
                self.failure=cause;self.condition.notify_all()
    def await_response(self,identity):
        deadline=time.monotonic()+WAIT_SECONDS
        with self.condition:
            while identity not in self.responses:
                if self.failure is not None:raise self.failure
                remaining=deadline-time.monotonic()
                assert remaining>0,'RPC response timeout: '+identity
                self.condition.wait(remaining)
            return self.responses[identity]
    def command(self,text):
        return self.await_response(self.send('sbt/exec',dict(commandLine=text)))
    def successful(self,text):
        result=self.command(text)
        assert 'error' not in result and result['result'].get('exitCode')==0,(text,result)
        return result
    def close(self):
        self.running=False
        self.socket.shutdown(socket.SHUT_RDWR);self.socket.close();self.reader.join(10)


def close_server(client: Client | None, process: subprocess.Popen, evidence: Path, log: TextIO, server: tempfile.TemporaryDirectory) -> None:
    if client is not None:
        (evidence/'rpc.json').write_text(json.dumps(client.frames,indent=2)+'\n')
        client.close()
    if process.poll() is None:
        terminate_process(process, SERVER_SHUTDOWN_GRACE_SECONDS)
    log.close()
    server.cleanup()

def wait(condition,process,label,seconds):
    deadline=time.monotonic()+seconds
    while not condition() and time.monotonic()<deadline:
        assert process.poll() is None,'Server exited: '+label
        time.sleep(.01)
    assert condition(),label
def verify(capture,row,runs,resources):
    audit=capture/'audit'
    bodies=[p.read_text().split('\t') for p in audit.glob('*.body')]
    cancelled=row['mode'].startswith('cancel')
    assert len(bodies)==(1 if cancelled else 15),'CLIENT_BODY_COUNT'
    expected={(f'fixture.Suite{s}',str(i)) for s in 'ABCDE' for i in range(1,4)}
    observed={(b[0],b[1]) for b in bodies}
    assert observed.issubset(expected) and (cancelled or observed==expected)
    acquired=[p.read_text() for p in audit.glob('*.acquire')]
    released=[p.read_text() for p in audit.glob('*.release')]
    assert len(acquired)==1 and acquired==released and acquired[0] not in resources
    resources.update(acquired)
    assert all(b[2]==acquired[0] for b in bodies)
    parent=(audit/'host.pid').read_text();pids={b[3] for b in bodies}
    assert len(pids)==1 and ((parent in pids)==(not row['fork']))
    files=list((capture/'events').glob('*.jsonl'));assert len(files)==1,'CLIENT_STREAM_COUNT'
    frames=[json.loads(line)['message'] for line in files[0].read_text().splitlines()]
    assert frames[-1]['kind']=='completed','CLIENT_OUTCOME_INCOMPLETE'
    outcome=frames[-1]['outcome'];assert outcome['run'] not in runs;runs.add(outcome['run'])
    assert len(outcome['results'])==15 and outcome['cancelled']==cancelled
    assert all(r['status']==('cancelled' if cancelled else 'succeeded') for r in outcome['results'])
    nodes=[node for p in (capture/'test-reports').glob('*.xml') for node in ElementTree.parse(p).findall('.//testcase')]
    selected=[n for n in nodes if n.attrib['name'].startswith('same display name should ')]
    assert len(selected)==15,'CLIENT_XML_INCOMPLETE'
    if cancelled:
        assert all(n.find('skipped') is not None and n.find('skipped').attrib.get('message')=='Cancelled' for n in selected),'CLIENT_CANCELLED_XML_LOOKS_PASSED'
        assert sum(int(ElementTree.parse(p).getroot().attrib['skipped']) for p in (capture/'test-reports').glob('*.xml'))==15,'CLIENT_CANCELLED_XML_COUNT'
        assert (audit/'release.observed-held').read_text()=='held'
        assert (audit/'body.interrupted').read_text() in pids
        assert 'error' in row['response'] or row['response']['result'].get('exitCode')!=0,'CLIENT_FALSE_SUCCESS'
        assert row['cancelResponse']['result']['status']=='Task cancelled'
        if row['mode']=='cancel-release-error':assert any(f['phase']=='finalization' and 'CANCEL_RELEASE_FAILURE' in json.dumps(f) for f in outcome['failures'])
    else:assert not outcome['failures'] and all(n.find('error') is None and n.find('failure') is None for n in nodes)
    return dict(**row,bodies=len(bodies),results=15,xmlCases=len(nodes),resource=acquired[0],run=outcome['run'],hostPid=parent,bodyPids=sorted(pids))
def main():
    parser = consumer_parser()
    parser.add_argument('--scala-version',choices=['3.9.0','2.13.18'],required=True)
    parser.add_argument('--execution-mode',choices=['both','inprocess','fork'],default='both')
    args=parser.parse_args();root=args.repo_root.resolve();out=args.evidence_dir.resolve();out.mkdir()
    # Reuse the identical suite/resource fixture without its task-interruption proxy.
    path=root/'test-fixtures/sbt-plugin-consumer/verify-task-cancellation.py'
    fixture = load_module('task_fixture', path)
    build=out/'build'
    source=build/'src/test/scala';source.mkdir(parents=True)
    (source/'Suites.scala').write_text(fixture.SOURCE.replace('@AUDIT@',json.dumps(str(build/'audit')))+'\n'+'\n'.join(f'final class {s} extends CancellationSuite' for s in fixture.SUITES)+'\n')
    (source/'Plugin.scala').write_text(fixture.PLUGIN)
    settings=fixture.SETTINGS[:fixture.SETTINGS.index('val proxyFramework')] + fixture.SETTINGS[fixture.SETTINGS.index('lazy val prepareCancellation'):]
    write_sbt_project(build, settings.replace('@SCALA@',json.dumps(args.scala_version)).replace('@VERSION@',json.dumps(args.artifact_version)).replace('@CAPTURES@',json.dumps(str(out/'cases'))), '2.0.9', 'addSbtPlugin("io.7mind.izumi" % "sbt-distage-testkit" % '+json.dumps(args.artifact_version)+')\n')
    argv=['direnv','exec',str(root),'sh','-c','exec sbt --server --sbt-version 2.0.9 -java-home "$JDK21" -batch -J-Xmx6G "$@"','client-cancellation','--detach-stdio','startServer','shell']
    inputs=[dict(path=str(p),sha256=hashlib.sha256(p.read_bytes()).hexdigest()) for p in sorted(build.rglob('*')) if p.is_file()]
    (out/'command.json').write_text(json.dumps(dict(argv=argv,cwd=str(build),inputs=inputs),indent=2)+'\n')
    client=None;cases=[];checks=[];failures=[];runs=set();resources=set();log=(out/'server.log').open('w')
    server_directory=tempfile.TemporaryDirectory(prefix='distage-rpc-',dir=root/'target')
    process=subprocess.Popen(argv,cwd=build,stdin=subprocess.PIPE,stdout=log,stderr=subprocess.STDOUT,start_new_session=True,env=dict(os.environ,SBT_GLOBAL_SERVER_DIR=server_directory.name))
    try:
        active=build/'project/target/active.json'
        wait(active.exists,process,'Client server did not publish address',WAIT_SECONDS)
        client=Client(json.loads(active.read_text()))
        initialized=client.await_response(client.send('initialize',dict(processId=os.getpid(),rootUri=build.as_uri(),capabilities={},initializationOptions=dict(skipAnalysis=True,canWork=False))))
        assert 'result' in initialized,initialized
        modes={'both':[False,True],'inprocess':[False],'fork':[True]}
        for fork in modes[args.execution_mode]:
            client.successful('set Test / fork := '+str(fork).lower())
            for index,mode in enumerate(fixture.MODES):
                name=('fork-' if fork else 'inprocess-')+str(index)+'-'+mode
                client.successful('prepareCancellation '+mode)
                command='test' if index==2 else 'testQuick' if index==4 else 'testOnly fixture.Suite*'
                identity=client.send('sbt/exec',dict(commandLine=command))
                row=dict(name=name,mode=mode,fork=fork,request=command,execId=identity)
                if mode.startswith('cancel'):
                    audit=build/'audit'
                    wait(lambda: bool(list(audit.glob('*.body'))),process,'Client cancel body did not enter',WAIT_SECONDS)
                    cancelled=client.send('sbt/cancelRequest',dict(id=CANCEL_CURRENT_CHANNEL))
                    row['cancelRequestId']=cancelled
                    (out/'live-case.json').write_text(json.dumps(row,indent=2)+'\n')
                    wait(lambda: bool(list(audit.glob('*.release-entered'))) or 'error' in client.responses.get(cancelled,{}),process,'CLIENT_FINALIZER_DID_NOT_ENTER',RELEASE_WAIT_SECONDS)
                    if 'error' in client.responses.get(cancelled,{}):
                        raise AssertionError('Client cancellation rejected: '+json.dumps(client.responses[cancelled]))
                    time.sleep(RELEASE_HOLD_SECONDS)
                    assert not list(audit.glob('*.release')),'CLIENT_FINALIZER_IGNORED_HOLD'
                    assert identity not in client.responses,'CLIENT_COMMAND_RETURNED_BEFORE_FINALIZER'
                    (audit/'release.observed-held').write_text('held')
                    (audit/'allow-release').write_text('release')
                    row['cancelResponse']=client.await_response(cancelled)
                row['response']=client.await_response(identity)
                cases.append(row)
                print('CLIENT_CASE',name,json.dumps(row['response']),flush=True)
                client.successful('captureCancellation '+name)
                checks.append(verify(out/'cases'/name,row,runs,resources))
                if mode.startswith('cancel'):
                    recovery_name = name+'-same-boundary-recovery'
                    client.successful('prepareCancellation recovery')
                    recovery = dict(name=recovery_name,mode='recovery',fork=fork,request='testOnly fixture.Suite*')
                    recovery['response'] = client.command(recovery['request'])
                    recovery['execId'] = recovery['response']['id']
                    cases.append(recovery)
                    print('CLIENT_CASE',recovery_name,json.dumps(recovery['response']),flush=True)
                    client.successful('captureCancellation '+recovery_name)
                    checks.append(verify(out/'cases'/recovery_name,recovery,runs,resources))
        client.send('sbt/exec',dict(commandLine='shutdown'))
        actual=process.wait(timeout=30);assert actual==0
    except BaseException as cause:
        failures.append(repr(cause)+'\n'+traceback.format_exc());print(failures[-1],flush=True)
        actual=process.poll()
        audit=build/'audit'
        pids={process.pid}
        pids.update(int(p.read_text().split('\t')[3]) for p in audit.glob('*.body'))
        for pid in pids:
            if not Path('/proc/'+str(pid)).exists():continue
            dump=['direnv','exec',str(root),'sh','-c','exec "$JDK21/bin/jcmd" "$@"','dump',str(pid),'Thread.print']
            with (out/('threads-'+str(pid)+'.txt')).open('w') as dumpfile:
                subprocess.run(dump,cwd=root,stdout=dumpfile,stderr=subprocess.STDOUT,timeout=30)
    finally:
        close_server(client, process, out, log, server_directory)
    result=dict(exit=int(bool(failures)),actualExit=actual,scala=args.scala_version,cases=cases,checks=checks,failures=failures)
    (out/'completion.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(dict(exit=result['exit'],completedCases=len(cases))),flush=True)
    raise SystemExit(result['exit'])
if __name__=='__main__':main()
