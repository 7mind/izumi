#!/usr/bin/env python3
from pathlib import Path
from xml.etree import ElementTree
import argparse, hashlib, importlib.util, json, os, shutil, signal, subprocess, time

ROOT = Path(__file__).resolve().parents[2]
TIMEOUT_SECONDS = 360
HOLD_SECONDS = 2
GRACE_SECONDS = 10
PREFIX = 'izumi.fixtures.host.'
ALL = [PREFIX + 'Suite' + c for c in 'ABCDE'] + [PREFIX + 'ForeignSuite']
CASES = [
    dict(name='normal-all', grouping='single', excluded='none', held=False, selected=ALL, requested=ALL, resources=1),
    dict(name='held-all', grouping='single', excluded='none', held=True, selected=ALL, requested=ALL, resources=1),
    dict(name='held-partial', grouping='single', excluded='none', held=True, selected=[ALL[0], ALL[5]], requested=[ALL[0], ALL[5]], resources=1),
    dict(name='held-excluded', grouping='single', excluded=ALL[4], held=True, selected=ALL[:4]+ALL[5:], requested=[PREFIX+'*'], resources=1),
    dict(name='held-ordered', grouping='single', excluded='none', held=True, selected=[ALL[0], ALL[1], ALL[5]], requested=[ALL[1], ALL[5], ALL[0], ALL[1]], resources=1),
    dict(name='held-split', grouping='split', excluded='none', held=True, selected=ALL, requested=ALL, resources=2),
    dict(name='held-foreign-group', grouping='foreign', excluded='none', held=True, selected=ALL, requested=ALL, resources=1),
    dict(name='normal-empty-foreign-group', grouping='foreign', excluded='none', held=False, selected=ALL[:2], requested=ALL[:2], resources=1),
    dict(name='normal-empty-selection', grouping='foreign', excluded=ALL[0], held=False, selected=[], requested=[ALL[0]], resources=0),
]

SETTINGS = r'''
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, StandardCopyOption, StandardOpenOption}
final class DynamicCompletionFiles(val root: File) {
  def token(name: String): String = name.getBytes(StandardCharsets.UTF_8).map(b => "%02x".format(b & 255)).mkString
  def names(file: File): Vector[String] = IO.read(file).split("\n", -1).toVector.filter(_.nonEmpty)
  def publish(file: File, text: String): Unit = {
    IO.createDirectory(file.getParentFile)
    if (file.isFile) require(IO.read(file) == text, "Duplicate completion content differs")
    else {
      val temporary = Files.createTempFile(file.getParentFile.toPath, "publication-", ".tmp")
      try {
        val written = Files.write(temporary, text.getBytes(StandardCharsets.UTF_8), StandardOpenOption.WRITE)
        require(Files.isRegularFile(written), "Completion temporary missing")
        val moved = Files.move(temporary, file.toPath, StandardCopyOption.ATOMIC_MOVE)
        require(Files.isRegularFile(moved), "Completion publication missing")
      } finally { val _ = Files.deleteIfExists(temporary) }
    }
  }
  def expected(directory: File): Vector[String] = names(directory / "declared").filter(name => (root / "selected" / token(name)).isFile)
}
val completionFramework = new TestFramework("izumi.fixtures.host.DynamicCompletionFramework")
Test / testFrameworks := ORDER
Test / fork := true
Test / testOptions += Tests.Filter { name =>
  val excluded = IO.read(file(sys.props("izumi.fixture.audit-root")) / "excluded")
  name != excluded
}
Test / testSelected / testFilter := Def.uncached {
  val inherited = (Test / testSelected / testFilter).value
  val files = new DynamicCompletionFiles(file(sys.props("izumi.fixture.audit-root")))
  (arguments: Seq[String]) => inherited(arguments).map { filter => (name: String) =>
    val included = filter(name)
    if (included) files.publish(files.root / "selected" / files.token(name), name)
    included
  }
}
Test / testSelected / testGrouping := Def.uncached {
  val inherited = (Test / testSelected / testGrouping).value
  val files = new DynamicCompletionFiles(file(sys.props("izumi.fixture.audit-root")))
  val mode = IO.read(files.root / "grouping")
  val expanded = inherited.flatMap { group =>
    val partitions = mode match {
      case "single" => Seq(group.tests)
      case "split" =>
        val (left, right) = group.tests.partition(test => Set("izumi.fixtures.host.SuiteA", "izumi.fixtures.host.SuiteB", "izumi.fixtures.host.SuiteC").contains(test.name))
        Seq(left, right)
      case "foreign" =>
        val (foreign, owned) = group.tests.partition(_.name == "izumi.fixtures.host.ForeignSuite")
        Seq(owned, foreign)
      case other => throw new IllegalArgumentException("Invalid grouping: " + other)
    }
    partitions.zipWithIndex.map { case (definitions, index) =>
      new Tests.Group(group.name + "-" + index, definitions, group.runPolicy, group.tags)
    }
  }
  expanded.zipWithIndex.map { case (group, index) =>
    val directory = files.root / "groups" / ("group-" + index)
    files.publish(directory / "declared", group.tests.map(_.name).distinct.sorted.mkString("\n"))
    val policy = group.runPolicy match {
      case Tests.SubProcess(options) =>
        require(!options.runJVMOptions.exists(_.startsWith("-Dizumi.fixture.completion-group=")), "Inherited completion group option")
        Tests.SubProcess(options.withRunJVMOptions(options.runJVMOptions :+ ("-Dizumi.fixture.completion-group=" + directory.getAbsolutePath)))
      case Tests.InProcess => throw new IllegalArgumentException("Dynamic completion fixture requires a fork")
    }
    new Tests.Group(group.name, group.tests, policy, group.tags)
  }
}
Test / testSelected / testExecution := Def.uncached {
  val inherited = (Test / testSelected / testExecution).value
  val original = new TestFramework("izumi.distage.testkit.runner.bootstrap.Framework")
  val directories = inherited.options.collect {
    case Tests.Argument(Some(owner), values) if owner == original && values.headOption.contains("--distage-host-receipts") => values
  }
  require(directories.size == 1 && directories.head.size == 2, "Command directory argument missing")
  val files = new DynamicCompletionFiles(file(sys.props("izumi.fixture.audit-root")))
  files.publish(files.root / "host-command.fork-dir", directories.head(1))
  inherited.copy(options = inherited.options.map {
    case Tests.Argument(Some(owner), values) if owner == original => Tests.Argument(completionFramework, values: _*)
    case other => other
  })
}
Test / testListeners += {
  val files = new DynamicCompletionFiles(file(sys.props("izumi.fixture.audit-root")))
  val receiptParent = target.value / "distage-fork-receipts"
  val HostWaitSeconds = 25L
  val PollMillis = 5L
  new TestsListener {
    override def doInit(): Unit = {
      val dirs = (receiptParent * "command-*").get().filter(_.isDirectory)
      require(dirs.size == 1, "Expected one command receipt directory")
      files.publish(files.root / "host-command.fork-dir", dirs.head.getCanonicalPath)
      files.publish(files.root / "host.parent-pid", ProcessHandle.current().pid().toString)
    }
    override def startGroup(name: String): Unit = ()
    override def testEvent(event: TestEvent): Unit = {
      if (IO.read(files.root / "held") == "true" && event.detail.exists(_.fullyQualifiedName() == "izumi.fixtures.host.ForeignSuite")) {
        require(event.detail.size == 3 && event.detail.forall(_.status() == sbt.testing.Status.Success), "Foreign batch differs")
        files.publish(files.root / "host.foreign-held", ProcessHandle.current().pid().toString)
        val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(HostWaitSeconds)
        while (!(files.root / "host.foreign-allow").isFile && System.nanoTime() < deadline) Thread.sleep(PollMillis)
        require((files.root / "host.foreign-allow").isFile, "Foreign callback was not released")
        files.publish(files.root / "host.foreign-returned", ProcessHandle.current().pid().toString)
      }
    }
    override def endGroup(name: String, cause: Throwable): Unit = throw new IllegalStateException("Dynamic group failed: " + name, cause)
    override def endGroup(name: String, result: TestResult): Unit = synchronized {
      require(result == TestResult.Passed, "Dynamic group failed")
      val marker = files.root / "ended" / files.token(name)
      require(!marker.exists(), "Duplicate dynamic suite group")
      files.publish(marker, name)
      val groups = (files.root / "groups" * "group-*").get().filter(_.isDirectory)
      val matching = groups.filter(group => files.expected(group).contains(name))
      require(matching.size == 1, "Completed suite has no unique fork group")
      val group = matching.head
      val expected = files.expected(group)
      if (expected.forall(suite => (files.root / "ended" / files.token(suite)).isFile)) {
        files.publish(group / "host-ack", expected.mkString("\n"))
        val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(HostWaitSeconds)
        while (!(group / "child-ready").isFile && System.nanoTime() < deadline) Thread.sleep(PollMillis)
        require((group / "child-ready").isFile, "Per-fork child acknowledgement missing")
        require(IO.read(group / "child-ready") == IO.read(group / "shutdown-enter"), "Child acknowledgement PID differs")
        files.publish(group / "host-return", ProcessHandle.current().pid().toString)
      }
    }
    override def doComplete(result: TestResult): Unit = {
      require(result == TestResult.Passed, "Dynamic command failed")
      val names = (files.root / "selected" * "*").get().map(IO.read(_)).sorted
      files.publish(files.root / "host.completed", names.mkString("\n"))
    }
  }
}
Test / testSelected / testResultLogger := {
  val inherited = (Test / testSelected / testResultLogger).value
  new TestResultLogger {
    override def run(log: sbt.util.Logger, output: Tests.Output, taskName: String): Unit = {
      val files = new DynamicCompletionFiles(file(sys.props("izumi.fixture.audit-root")))
      val expected = files.names(files.root / "expected").sorted
      val rows = output.events.toVector.sortBy(_._1).map { case (name, result) => name + "\t" + result.result + "\t" + result.passedCount + "\t" + result.failureCount + "\t" + result.errorCount + "\t" + result.skippedCount }
      files.publish(files.root / "host.result-counts", rows.mkString("\n"))
      files.publish(files.root / "host.result-parent-pid", ProcessHandle.current().pid().toString)
      require(output.events.keys.toVector.sorted == expected && output.events.values.forall(result => result.result == TestResult.Passed && result.passedCount == 3 && result.failureCount == 0 && result.errorCount == 0 && result.skippedCount == 0), "DYNAMIC_FORK_RESULT_SET_DIFFERS")
      inherited.run(log, output, taskName)
    }
  }
}
val prepareDynamicCase = inputKey[Unit]("Prepare explicit dynamic selection/grouping inputs")
prepareDynamicCase := {
  val parsed = spaceDelimited("case grouping excluded held expected suites").parsed
  require(parsed.size >= 4, "Dynamic case inputs missing")
  val audit = file(sys.props("izumi.fixture.audit-root"))
  require(audit.getCanonicalFile == (baseDirectory.value / "target/body-audit").getCanonicalFile, "Unowned dynamic audit")
  IO.delete(audit)
  IO.createDirectory(audit)
  IO.delete((Test / target).value / "test-reports")
  val files = new DynamicCompletionFiles(audit)
  files.publish(audit / "case", parsed(0))
  files.publish(audit / "grouping", parsed(1))
  files.publish(audit / "excluded", parsed(2))
  files.publish(audit / "held", parsed(3))
  files.publish(audit / "expected", parsed.drop(4).sorted.mkString("\n"))
  streams.value.log.info("DYNAMIC_FORK_PREPARED case=" + parsed(0))
}
val captureDynamicCase = inputKey[Unit]("Freeze dynamic completion evidence")
captureDynamicCase := {
  val parsed: Seq[String] = spaceDelimited("case").parsed;
  Def.uncached {
    require(parsed.size == 1, "Dynamic capture case missing")
    val capture = file(sys.props("izumi.fixture.captures")) / parsed.head
    require(!capture.exists(), "Dynamic capture must be new")
    IO.copyDirectory(file(sys.props("izumi.fixture.audit-root")), capture / "body-audit")
    IO.copyDirectory((Test / target).value / "test-reports", capture / "test-reports")
    val parent = (Test / target).value / "distage-fork-receipts"
    require(parent.exists() && (parent * "*").get().isEmpty, "Command receipt survived")
    streams.value.log.info("DYNAMIC_FORK_CAPTURE_OK case=" + parsed.head)
  }
}
'''

HOOK = r'''
    val groupDirectory = if (child) Some(Paths.get(sys.props("izumi.fixture.completion-group"))) else None
    if (child) {
      val directory = groupDirectory.getOrElse(throw new IllegalStateException("Missing child group"))
      val audit = directory.getParent.getParent
      def token(name: String): String = name.getBytes(StandardCharsets.UTF_8).map(b => "%02x".format(b & 255)).mkString
      val declared = new String(Files.readAllBytes(directory.resolve("declared")), StandardCharsets.UTF_8).split("\n", -1).toVector.filter(_.nonEmpty)
      val selected = declared.filter(name => Files.isRegularFile(audit.resolve("selected").resolve(token(name))))
      require(selected.nonEmpty, "Dynamic control requires a nonempty selected group")
      def publish(name: String, text: String): Unit = {
        val temporary = Files.createTempFile(directory, "publication-", ".tmp")
        try {
          val written = Files.write(temporary, text.getBytes(StandardCharsets.UTF_8), StandardOpenOption.WRITE)
          require(Files.isRegularFile(written), "Child publication temporary missing")
          val moved = Files.move(temporary, directory.resolve(name), java.nio.file.StandardCopyOption.ATOMIC_MOVE)
          require(Files.isRegularFile(moved), "Child publication missing")
        } finally { val _ = Files.deleteIfExists(temporary) }
      }
      val HostWaitSeconds = 25L
      val PollMillis = 5L
      val shutdown = new Thread(new Runnable {
        override def run(): Unit = {
          val pid = ProcessHandle.current().pid().toString
          publish("shutdown-enter", pid)
          val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(HostWaitSeconds)
          val ack = directory.resolve("host-ack")
          while (!Files.isRegularFile(ack) && System.nanoTime() < deadline) Thread.sleep(PollMillis)
          require(Files.isRegularFile(ack), "Per-fork host acknowledgement missing")
          require(new String(Files.readAllBytes(ack), StandardCharsets.UTF_8) == selected.mkString("\n"), "Per-fork selected group set differs")
          publish("child-ready", pid)
        }
      }, "fixture-owned-per-fork-shutdown")
      Runtime.getRuntime.addShutdownHook(shutdown)
    }
'''

def sha(path): return hashlib.sha256(Path(path).read_bytes()).hexdigest()

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--evidence-dir',required=True,type=Path)
    parser.add_argument('--artifact-version',required=True)
    parser.add_argument('--sbt-version',required=True,choices=['2.0.9'])
    parser.add_argument('--scala-version',required=True,nargs='+',choices=['3.9.0','2.13.18','2.12.21'])
    parser.add_argument('--framework-order',required=True,choices=['own-first','foreign-first'])
    args=parser.parse_args()
    out=args.evidence_dir.resolve(); out.mkdir(exist_ok=False); shutil.copy2(__file__,out/'driver.py')
    helper=ROOT/'test-fixtures/host-sharing-consumer/verify-held-forks.py'
    spec=importlib.util.spec_from_file_location('held_source',helper); module=importlib.util.module_from_spec(spec); spec.loader.exec_module(module)
    source=module.SOURCE.replace('HeldDeliveryFramework','DynamicCompletionFramework').replace('distage-held-delivery-control','distage-dynamic-completion-control')
    source=source.replace('    new Runner {',HOOK+'    new Runner {')
    source=source.replace('val directory = Paths.get(sys.props("izumi.fixture.audit-root"))', 'val directory = if (child) groupDirectory.getOrElse(throw new IllegalStateException("Missing child group")) else Paths.get(sys.props("izumi.fixture.audit-root"))')
    shutil.copy2(helper,out/'source-helper.py');(out/'completion-framework-source.scala').write_text(source)
    fixture=ROOT/'test-fixtures/host-sharing-consumer'; originals=[fixture/'build.sbt',*sorted((fixture/'src').rglob('*.scala'))]
    inputs=[dict(path=str(p),sha256=sha(p)) for p in originals];(out/'inputs.json').write_text(json.dumps(dict(sources=inputs,helper=str(helper),helperSha256=sha(helper)),indent=2)+'\n')
    outcomes=[]
    for scala in args.scala_version:
        lane=out/('sbt2.0.9-scala'+scala);build=lane/'build';build.mkdir(parents=True)
        for p in originals:
            target=build/p.relative_to(fixture);target.parent.mkdir(parents=True,exist_ok=True);value=p.read_text()
            if p.name=='build.sbt':
                start=value.index('Test / testFrameworks :=');end=value.index('Test / javaOptions +=',start);value=value[:start]+value[end:]
                order='Seq(completionFramework, new TestFramework("izumi.fixtures.host.ForeignFramework"))' if args.framework_order=='own-first' else 'Seq(new TestFramework("izumi.fixtures.host.ForeignFramework"), completionFramework)'
                value+='\nlazy val dynamicConsumer = project.in(file(".")).enablePlugins(izumi.distage.sbt.DistageTestkitPlugin)\n'+SETTINGS[SETTINGS.index('val completionFramework'):].replace('ORDER',order)
            elif p.name in ['SuiteA.scala','SuiteB.scala']:value=value.replace('extends PlainFixtureSuite','extends DIFixtureSuite')
            target.write_text(value)
        (build/'src/test/scala/izumi/fixtures/host/DynamicCompletionFramework.scala').write_text(source)
        project=build/'project';project.mkdir();(project/'DynamicCompletionFiles.scala').write_text('import sbt._\n'+SETTINGS[:SETTINGS.index('val completionFramework')]);(project/'build.properties').write_text('sbt.version=2.0.9\n');(project/'plugins.sbt').write_text('addSbtPlugin("io.7mind.izumi" % "sbt-distage-testkit" % "'+args.artifact_version+'")\n')
        audit=build/'target/body-audit'
        commands=['set Global / localCacheDirectory := file("'+str(lane/'local-cache')+'")']
        for case in CASES:
            commands+=['prepareDynamicCase '+ ' '.join([case['name'],case['grouping'],case['excluded'],str(case['held']).lower(),*case['selected']]),'testOnly '+' '.join(case['requested']),'captureDynamicCase '+case['name']]
        commands+=['show Test / dependencyClasspath','show Test / fullClasspath']
        shell='task_sdk="$1"; shift; exec sbt --server --sbt-version "$task_sdk" -java-home "$JDK21" -batch -J-Xmx6G "$@"'
        argv=['direnv','exec',str(ROOT),'sh','-c',shell,'dynamic-fork','2.0.9','-Dizumi.fixture.scala-version='+scala,'-Dizumi.fixture.version='+args.artifact_version,'-Dizumi.fixture.audit-root='+str(audit),'-Dizumi.fixture.captures='+str(lane/'cases'),*commands]
        generated=[dict(path=str(p),sha256=sha(p)) for p in sorted(build.rglob('*')) if p.is_file()]
        (lane/'commands.json').write_text(json.dumps(dict(cwd=str(build),argv=argv,inputs=generated,cases=CASES),indent=2)+'\n')
        print('DYNAMIC_FORK_LANE_START '+scala+' '+args.framework_order,flush=True)
        observations={};started=time.monotonic()
        with (lane/'run.log').open('x') as log:
            process=subprocess.Popen(argv,cwd=build,stdout=log,stderr=subprocess.STDOUT,start_new_session=True)
            while process.poll() is None and time.monotonic()-started<TIMEOUT_SECONDS:
                current=(audit/'case').read_text() if (audit/'case').is_file() else None
                if current and current not in observations and (audit/'host.foreign-held').is_file():
                    groups=list((audit/'groups').glob('group-*'))
                    matching=[g for g in groups if ALL[5] in (g/'declared').read_text().splitlines()]
                    if len(matching)==1 and (matching[0]/'target.done-enter').is_file():
                        group=matching[0];time.sleep(HOLD_SECONDS)
                        pid=int((group/'target.done-enter').read_text().split('\t')[0].removeprefix('pid='));alive=True
                        try:os.kill(pid,0)
                        except ProcessLookupError:alive=False
                        observation=dict(case=current,parentPid=process.pid,childPid=pid,childAlive=alive,group=str(group),doneFinished=(group/'target.done-finish').exists(),shutdownEntered=(group/'shutdown-enter').exists(),shutdownReady=(group/'child-ready').exists(),globalHostAck=(group/'host-ack').exists(),foreignReturned=(audit/'host.foreign-returned').exists(),physical=[p.read_text() for p in sorted(audit.glob('*.body'))],selected=[p.read_text() for p in sorted((audit/'selected').glob('*'))],parentOutputBeforeAllow=(audit/'host.result-counts').read_text() if (audit/'host.result-counts').is_file() else None)
                        control=next(c for c in CASES if c['name']==current)
                        groupSelected=sorted(set((group/'declared').read_text().splitlines()).intersection(control['selected']))
                        expectedFinished=args.framework_order=='own-first' or groupSelected==[ALL[5]]
                        physicalIds=[tuple(r.split('\t')[:2]) for r in observation['physical']]
                        groupIds={(s,str(i)) for s in groupSelected for i in range(1,4)}
                        allIds={(s,str(i)) for s in control['selected'] for i in range(1,4)}
                        observation['groupSelected']=groupSelected
                        observation['valid']=alive and not observation['shutdownReady'] and not observation['globalHostAck'] and not observation['foreignReturned'] and observation['parentOutputBeforeAllow'] is None and observation['doneFinished']==expectedFinished and observation['shutdownEntered']==expectedFinished and groupIds.issubset(physicalIds) and set(physicalIds).issubset(allIds) and len(set(physicalIds))==len(physicalIds)
                        observations[current]=observation;(lane/('held-'+current+'.json')).write_text(json.dumps(observation,indent=2)+'\n')
                        with (audit/'host.foreign-allow').open('x') as f:f.write('release after frozen per-fork observation\n')
                        print('DYNAMIC_FORK_RELEASED '+current,flush=True)
                time.sleep(0.02)
            if process.poll() is None:
                os.killpg(process.pid,signal.SIGTERM)
                try:process.wait(timeout=GRACE_SECONDS)
                except subprocess.TimeoutExpired:os.killpg(process.pid,signal.SIGKILL);process.wait()
                actual=124
            else:actual=process.wait()
            log.write('\nEXIT '+str(actual)+'\n')
        failures=[];cases=[];resource_ids=set();receipt_dirs=set();parent_pids=set()
        if actual!=0:failures.append('Actual SBT process did not complete every dynamic case')
        for case in CASES:
            capture=lane/'cases'/case['name'];body=capture/'body-audit'
            if not body.is_dir():failures.append('Missing capture: '+case['name']);continue
            selected=sorted(case['selected']);physical=[p.read_text().split('\t') for p in body.glob('*.body')]
            if sorted((r[0],r[1]) for r in physical)!=[(s,str(i)) for s in selected for i in range(1,4)]:failures.append('Physical IDs differ: '+case['name'])
            acquired=[p.read_text() for p in body.glob('*.acquire')];released=[p.read_text() for p in body.glob('*.release')]
            if len(acquired)!=case['resources'] or sorted(acquired)!=sorted(released) or len(set(acquired))!=len(acquired) or resource_ids.intersection(acquired):failures.append('Fresh paired resources differ: '+case['name'])
            resource_ids.update(acquired)
            if any(r[2] not in acquired for r in physical if r[0]!=ALL[5]) or any(r[2]!='plain' for r in physical if r[0]==ALL[5]):failures.append('Physical resource ownership differs: '+case['name'])
            output=[r.split('\t') for r in (body/'host.result-counts').read_text().splitlines()]
            if output!=[[s,'Passed','3','0','0','0'] for s in selected]:failures.append('SDK output differs: '+case['name'])
            xml=[];ids=[]
            for p in sorted((capture/'test-reports').glob('*.xml')):
                tree=ElementTree.parse(p);summary=tree.getroot().attrib
                if summary['tests']!='3' or any(summary[k]!='0' for k in ['errors','failures','skipped']):failures.append('XML outcome differs: '+case['name'])
                ids.extend((n.attrib['classname'],n.attrib['name']) for n in tree.findall('.//testcase'));xml.append(dict(path=str(p),summary=summary))
            if sorted(ids)!=sorted((s,'equal display name should '+leaf) for s in selected for leaf in ['first','second','third']):failures.append('XML IDs differ: '+case['name'])
            observed_selected=sorted(p.read_text() for p in (body/'selected').glob('*'))
            completed=(body/'host.completed').read_text().splitlines() if (body/'host.completed').is_file() else None
            if observed_selected!=selected or completed!=(selected if selected else None):failures.append('Effective selected names differ: '+case['name'])
            parent=(body/'host.result-parent-pid').read_text();parent_pids.add(parent)
            if selected and (body/'host.parent-pid').read_text()!=parent:failures.append('Listener/logger parent PID differs: '+case['name'])
            groups=[];union=[];children=set()
            for g in sorted((body/'groups').glob('group-*')):
                declared=(g/'declared').read_text().splitlines();expected=sorted(set(declared).intersection(selected));union+=expected
                if not expected:
                    if any((g/n).exists() for n in ['shutdown-enter','child-ready','host-ack','host-return','target.done-enter','target.done-finish']):failures.append('Empty group started a child or callback: '+case['name'])
                    groups.append(dict(declared=declared,selected=[],childPid=None,ownedTasks=None))
                    continue
                pid=(g/'shutdown-enter').read_text()
                if (g/'host-ack').read_text().splitlines()!=expected or (g/'child-ready').read_text()!=pid or (g/'host-return').read_text()!=parent or pid in children or pid==parent:failures.append('Per-fork acknowledgement differs: '+case['name'])
                children.add(pid)
                if (g/'target.done-enter').read_text()!=(g/'target.done-finish').read_text():failures.append('Own completion differs: '+case['name'])
                own=(g/'target.done-enter').read_text().split('\t')[1].removeprefix('suites=')
                if own!=','.join(s for s in expected if s!=ALL[5]):failures.append('Owned per-fork task membership differs: '+case['name'])
                groups.append(dict(declared=declared,selected=expected,childPid=pid,ownedTasks=own))
            if sorted(union)!=selected or len(groups)!=(1 if case['grouping']=='single' else 2):failures.append('Per-fork partition differs: '+case['name'])
            receipt=Path((body/'host-command.fork-dir').read_text())
            receiptParent=receipt.parent
            if not receiptParent.is_dir() or list(receiptParent.iterdir()) or (receipt is not None and (receipt.exists() or not receipt.is_relative_to(build/'target') or str(receipt) in receipt_dirs)):failures.append('Command receipt ownership/cleanup differs: '+case['name'])
            receipt_dirs.add(str(receipt))
            if case['held'] and (case['name'] not in observations or not observations[case['name']]['valid']):failures.append('Held per-fork state differs: '+case['name'])
            cases.append(dict(name=case['name'],selected=selected,physicalBodies=len(physical),outputCases=sum(int(r[2]) for r in output),xmlCases=len(ids),resources=acquired,parentPid=parent,groups=groups,receiptDirectory=str(receipt) if receipt is not None else None))
        if len(parent_pids)!=1:failures.append('Commands did not share one SBT process')
        raw=(lane/'run.log').read_text()
        if actual==0 and 'Reapplying settings' in raw.split('DYNAMIC_FORK_PREPARED case=normal-all',1)[1]:failures.append('Unexpected settings reapplication')
        for r in inputs+generated:assert sha(r['path'])==r['sha256']
        assert sha(helper)==sha(out/'source-helper.py') and sha(__file__)==sha(out/'driver.py')
        row=dict(scala=scala,frameworkOrder=args.framework_order,actualExit=actual,validationFailures=failures,cases=cases,observations=observations,scope='Public SDK2 effective ordered-filter observation and per-fork Java-option membership prototype; excludes/partials/ordered overlap/two forks/foreign-only group. Empty selected groups start no child and receive no ACK; all-empty command has no listener initialization. Production sources unchanged. Cache/failure/cancellation/cleanup/error/logger drain and overlapping group membership remain open.')
        (lane/'completion.json').write_text(json.dumps(row,indent=2)+'\n');outcomes.append(row)
        print(json.dumps(dict(scala=scala,actualExit=actual,validationFailures=failures,cases=len(cases),physicalBodies=sum(c['physicalBodies'] for c in cases))),flush=True)
        if failures:break
    terminal=dict(exit=0 if len(outcomes)==len(args.scala_version) and all(not r['validationFailures'] for r in outcomes) else 1,outcomes=outcomes)
    (out/'completion.json').write_text(json.dumps(terminal,indent=2)+'\n');print(json.dumps(dict(exit=terminal['exit'],sha256=sha(out/'completion.json'))),flush=True)
    raise SystemExit(terminal['exit'])

if __name__=='__main__':main()
