#!/usr/bin/env python3
from pathlib import Path
from xml.etree import ElementTree
import argparse, hashlib, importlib.util, json, os, shutil, signal, subprocess

ROOT=Path(__file__).resolve().parents[2]
TIMEOUT_SECONDS=240
GRACE_SECONDS=10

def sha(path):return hashlib.sha256(Path(path).read_bytes()).hexdigest()
def definitions(path,name):
    spec=importlib.util.spec_from_file_location(name,path)
    module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
    return module

EXTRA=r'''
val changeAckMode = inputKey[Unit]("Choose explicit normal or halt-after-ack target completion")
changeAckMode := {
  val parsed = spaceDelimited("mode").parsed
  require(parsed.size == 1 && Set("normal", "halt-after-ack").contains(parsed.head), "Ack fault mode missing")
  val files = new DynamicCompletionFiles(file(sys.props("izumi.fixture.audit-root")))
  files.publish(files.root / "ack-fault-mode", parsed.head)
}
val verifyAckRejection = taskKey[Unit]("Require acknowledgement invariant failure through the public input-task result")
verifyAckRejection := Def.uncached {
  val result = (Test / testSelected).toTask(" SELECTED").result.value
  val failure = result.toEither match {
    case Left(cause) => cause
    case Right(_) => throw new IllegalStateException("ACK_BOUNDARY_FALSE_SUCCESS")
  }
  require(failure.toString.contains("INCOMPLETE_DYNAMIC_FORK_ACKNOWLEDGEMENT"), "Unexpected ack rejection cause: " + failure)
  val files = new DynamicCompletionFiles(file(sys.props("izumi.fixture.audit-root")))
  require((files.root / "host.ack-invariant-failure").isFile, "Parent acknowledgement failure was not recorded")
  files.publish(files.root / "host.task-failure", failure.toString)
  streams.value.log.info("ACK_BOUNDARY_REJECTED " + failure)
}
'''

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--artifact-version',required=True)
    parser.add_argument('--sbt-version',required=True,choices=['2.0.9'])
    parser.add_argument('--framework-order',required=True,choices=['own-first','foreign-first'])
    parser.add_argument('--scala-version',required=True,choices=['3.9.0','2.13.18','2.12.21'])
    parser.add_argument('--evidence-dir',required=True,type=Path)
    args=parser.parse_args()
    out=args.evidence_dir.resolve();out.mkdir(exist_ok=False);shutil.copy2(__file__,out/'driver.py')
    global_helper=ROOT/'test-fixtures/host-sharing-consumer/verify-global-exit-ack.py'
    held_helper=ROOT/'test-fixtures/host-sharing-consumer/verify-held-forks.py'
    control=definitions(global_helper,'global_membership');held=definitions(held_helper,'held_framework')
    hook=control.HOOK
    needle='          publish("child-ready", pid)'
    replacement='''          val mode = new String(Files.readAllBytes(audit.resolve("ack-fault-mode")), StandardCharsets.UTF_8)
          require(mode == "normal" || mode == "halt-after-ack", "Invalid target acknowledgement fault mode")
          if (mode == "halt-after-ack") {
            val halted = Files.write(directory.resolve("target.halt-after-ack"), pid.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
            require(Files.isRegularFile(halted), "Controlled target halt marker missing")
            Runtime.getRuntime.halt(0)
          }
          publish("child-ready", pid)'''
    assert hook.count(needle)==1;hook=hook.replace(needle,replacement)
    source=held.SOURCE.replace('HeldDeliveryFramework','DynamicCompletionFramework').replace('distage-held-delivery-control','distage-dynamic-completion-control')
    source=source.replace('    new Runner {',hook+'    new Runner {')
    source=source.replace('val directory = Paths.get(sys.props("izumi.fixture.audit-root"))','val directory = if (child) groupDirectory.getOrElse(throw new IllegalStateException("Missing child group")) else Paths.get(sys.props("izumi.fixture.audit-root"))')
    settings=control.SETTINGS
    assert 'host.ack-invariant-failure' in settings and 'INCOMPLETE_DYNAMIC_FORK_ACKNOWLEDGEMENT' in settings
    names=sorted(control.ALL)
    order='Seq(completionFramework, new TestFramework("izumi.fixtures.host.ForeignFramework"))' if args.framework_order=='own-first' else 'Seq(new TestFramework("izumi.fixtures.host.ForeignFramework"), completionFramework)'
    fixture=ROOT/'test-fixtures/host-sharing-consumer';originals=[fixture/'build.sbt',*sorted((fixture/'src').rglob('*.scala'))]
    build=out/'build';build.mkdir()
    for path in originals:
        target=build/path.relative_to(fixture);target.parent.mkdir(parents=True,exist_ok=True);value=path.read_text()
        if path.name=='build.sbt':
            start=value.index('Test / testFrameworks :=');end=value.index('Test / javaOptions +=',start)
            value=value[:start]+value[end:]+'\nlazy val ackBoundaryConsumer = project.in(file(".")).enablePlugins(izumi.distage.sbt.DistageTestkitPlugin)\n'
            value+=settings[settings.index('val completionFramework'):].replace('ORDER',order)
            value+=EXTRA.replace('SELECTED',' '.join(names))
        elif path.name in ['SuiteA.scala','SuiteB.scala']:value=value.replace('extends PlainFixtureSuite','extends DIFixtureSuite')
        target.write_text(value)
    (build/'src/test/scala/izumi/fixtures/host/DynamicCompletionFramework.scala').write_text(source)
    project=build/'project';project.mkdir()
    (project/'DynamicCompletionFiles.scala').write_text('import sbt._\n'+settings[:settings.index('val completionFramework')])
    (project/'build.properties').write_text('sbt.version=2.0.9\n')
    (project/'plugins.sbt').write_text('addSbtPlugin("io.7mind.izumi" % "sbt-distage-testkit" % "'+args.artifact_version+'")\n')
    sources=[dict(path=str(p),sha256=sha(p)) for p in originals+[global_helper,held_helper]]
    generated=[dict(path=str(p),sha256=sha(p)) for p in sorted(build.rglob('*')) if p.is_file()]
    for helper in [global_helper,held_helper]:shutil.copy2(helper,out/helper.name)
    (out/'completion-framework-source.scala').write_text(source)
    commands=['set Global / localCacheDirectory := file("'+str(out/'local-cache')+'")']
    for case,mode in [('baseline','normal'),('fault','halt-after-ack'),('recovery','normal')]:
        commands+=['prepareDynamicCase '+' '.join([case,'single','none','false',*names]),'changeAckMode '+mode,
                   'verifyAckRejection' if case=='fault' else 'testOnly '+' '.join(names),
                   'captureDynamicCase '+case]
    commands+=['show Test / dependencyClasspath','show Test / fullClasspath']
    shell='task_sdk="$1"; shift; exec sbt --server --sbt-version "$task_sdk" -java-home "$JDK21" -batch -J-Xmx6G "$@"'
    argv=['direnv','exec',str(ROOT),'sh','-c',shell,'ack-boundary',args.sbt_version,'-Dizumi.fixture.scala-version='+args.scala_version,'-Dizumi.fixture.version='+args.artifact_version,'-Dizumi.fixture.audit-root='+str(build/'target/body-audit'),'-Dizumi.fixture.captures='+str(out/'cases'),*commands]
    (out/'commands.json').write_text(json.dumps(dict(cwd=str(build),argv=argv,originalSources=sources,generatedSources=generated),indent=2)+'\n')
    print('ACK_BOUNDARY_START '+args.scala_version+' '+args.framework_order,flush=True)
    with (out/'run.log').open('x') as log:
        process=subprocess.Popen(argv,cwd=build,stdout=log,stderr=subprocess.STDOUT,start_new_session=True)
        try:actual=process.wait(timeout=TIMEOUT_SECONDS)
        except subprocess.TimeoutExpired:
            os.killpg(process.pid,signal.SIGTERM)
            try:process.wait(timeout=GRACE_SECONDS)
            except subprocess.TimeoutExpired:os.killpg(process.pid,signal.SIGKILL);process.wait()
            actual=124
        log.write('\nEXIT '+str(actual)+'\n')
    failures=[];cases=[];resources=set();receipts=set();parents=set();children=set()
    if actual!=0:failures.append('Actual SBT did not complete baseline/rejection/recovery')
    for case in ['baseline','fault','recovery']:
        directory=out/'cases'/case;audit=directory/'body-audit'
        if not audit.is_dir():failures.append('Missing case: '+case);continue
        physical=[p.read_text().split('\t') for p in audit.glob('*.body')]
        if sorted((r[0],r[1]) for r in physical)!=[(name,str(i)) for name in names for i in range(1,4)]:failures.append('Physical IDs differ: '+case)
        acquired=[p.read_text() for p in audit.glob('*.acquire')];released=[p.read_text() for p in audit.glob('*.release')]
        if len(acquired)!=1 or acquired!=released or resources.intersection(acquired):failures.append('Fresh paired resource differs: '+case)
        resources.update(acquired)
        if {r[2] for r in physical if r[0]!=control.ALL[5]}!=set(acquired) or any(r[2]!='plain' for r in physical if r[0]==control.ALL[5]):failures.append('Resource ownership differs: '+case)
        output=[r.split('\t') for r in (audit/'host.result-counts').read_text().splitlines()]
        if output!=[[name,'Passed','3','0','0','0'] for name in names]:failures.append('Public SDK output differs: '+case)
        group=audit/'groups/group-0';child=(group/'shutdown-enter').read_text();parent=(audit/'host.result-parent-pid').read_text()
        if child==parent or child in children:failures.append('Child PID ownership differs: '+case)
        children.add(child);parents.add(parent)
        if (audit/'host.parent-pid').read_text()!=parent or (group/'host-ack').read_text().splitlines()!=names:failures.append('Host group ownership differs: '+case)
        if case=='fault':
            if (group/'child-ready').exists() or (group/'host-return').exists() or (group/'target.halt-after-ack').read_text()!=child or not (audit/'host.ack-invariant-failure').is_file() or 'INCOMPLETE_DYNAMIC_FORK_ACKNOWLEDGEMENT' not in (audit/'host.task-failure').read_text():failures.append('Expected acknowledgement rejection differs')
        elif (group/'child-ready').read_text()!=child or (group/'host-return').read_text()!=parent or (audit/'host.task-failure').exists():failures.append('Normal child/task acknowledgement differs: '+case)
        receipt=Path((audit/'host-command.fork-dir').read_text())
        if receipt.exists() or not receipt.is_relative_to(build/'target') or str(receipt) in receipts or list(receipt.parent.iterdir()):failures.append('Fresh command cleanup differs: '+case)
        receipts.add(str(receipt))
        ids=[]
        for p in (directory/'test-reports').glob('*.xml'):
            tree=ElementTree.parse(p);summary=tree.getroot().attrib
            if summary['tests']!='3' or any(summary[key]!='0' for key in ['errors','failures','skipped']):failures.append('XML body outcome differs: '+case)
            ids.extend((node.attrib['classname'],node.attrib['name']) for node in tree.findall('.//testcase'))
        if sorted(ids)!=sorted((name,'equal display name should '+leaf) for name in names for leaf in ['first','second','third']):failures.append('XML body IDs differ: '+case)
        cases.append(dict(case=case,physicalBodies=len(physical),sdkBodySuccesses=sum(int(r[2]) for r in output),xmlBodySuccesses=len(ids),taskRejected=case=='fault',parentPid=parent,childPid=child,resource=acquired,receiptDirectory=str(receipt)))
    if len(parents)!=1 or len(children)!=3 or len(resources)!=3 or len(receipts)!=3:failures.append('Same-session recovery/fresh ownership differs')
    raw=(out/'run.log').read_text()
    if raw.count('ACK_BOUNDARY_REJECTED ')!=1 or raw.count('DYNAMIC_FORK_CAPTURE_OK case=')!=3 or 'Reapplying settings' in raw.split('DYNAMIC_FORK_PREPARED case=baseline',1)[1]:failures.append('Rejection/capture/settings markers differ')
    for row in sources+generated:assert sha(row['path'])==row['sha256']
    assert sha(__file__)==sha(out/'driver.py')
    terminal=dict(exit=0 if not failures else 1,sbt=args.sbt_version,scala=args.scala_version,frameworkOrder=args.framework_order,actualSbtExit=actual,validationFailures=failures,cases=cases,scope='Public SDK2 prototype retains ACK failure in a session-owned file and checks it plus ready/host-return at public result task boundary. Controlled halt0 is rejected; same-session explicit recovery succeeds with fresh resources. 54 successful BODY results are separate from one failed TASK. No production integration, structured run-error/XML projection, cache/fault/cancel or final acceptance claim.')
    (out/'completion.json').write_text(json.dumps(terminal,indent=2)+'\n')
    print(json.dumps(dict(exit=terminal['exit'],actualSbtExit=actual,validationFailures=failures,sha256=sha(out/'completion.json'))),flush=True)
    raise SystemExit(terminal['exit'])

if __name__=='__main__':main()
