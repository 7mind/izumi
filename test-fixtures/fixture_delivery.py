"""Named controls for host task-failure and omitted-suite delivery fixtures."""

from dataclasses import dataclass
import re


@dataclass(frozen=True)
class DeliveryControl:
    project: str
    framework: str
    command: str
    fault_mode: str
    fault_flag: str
    label: str
    run_label: str
    rejection: str


def render(template: str, values: dict[str, str]) -> str:
    for token, value in values.items():
        assert token in template, token
        template = template.replace(token, value)
    assert re.search(r'@[A-Z_]+@', template) is None
    return template


def delivery_framework(control: DeliveryControl, tasks: str) -> str:
    return render(FRAMEWORK, {'@FRAMEWORK@': control.framework, '@TASKS@': tasks})


def delivery_settings(control: DeliveryControl) -> str:
    return render(SETTINGS, {
        '@PROJECT@': control.project,
        '@FRAMEWORK@': control.framework,
        '@COMMAND@': control.command,
        '@FAULT_MODE@': control.fault_mode,
        '@FAULT_FLAG@': control.fault_flag,
        '@LABEL@': control.label,
        '@TITLE@': control.label.capitalize(),
        '@REJECTION@': control.rejection,
    })

DELIVERY_SHUTDOWN_GRACE_SECONDS = 10


def run_delivery(driver, root, args, control, framework, settings, check, report_name, expected_report, timeout_seconds):
    from pathlib import Path
    import json
    import shutil
    from fixture_harness import write_sbt_project, freeze_driver, run_process, sha

    out = args.evidence_dir.resolve()
    out.mkdir()
    freeze_driver(driver, out / 'driver.py')
    shutil.copy2(__file__, out / 'fixture_delivery.py')
    fixture = root / 'test-fixtures/host-sharing-consumer'
    outcomes = []
    label = control.run_label
    for scala in args.scala_version:
        lane = out / ('scala' + scala)
        build = lane / 'build'
        for source in sorted((fixture / 'src').rglob('*.scala')):
            target = build / source.relative_to(fixture)
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(source, target)
        (build / ('src/test/scala/izumi/fixtures/host/' + control.framework + '.scala')).write_text(framework)
        definition = (fixture / 'build.sbt').read_text()
        start = definition.index('Test / testFrameworks :=')
        end = definition.index('Test / javaOptions +=', start)
        write_sbt_project(build, definition[:start] + definition[end:] + settings, '2.0.9', 'addSbtPlugin("io.7mind.izumi" % "sbt-distage-testkit" % "' + args.artifact_version + '")\n')
        commands = ['set Global / localCacheDirectory := file("' + str(lane / 'local-cache') + '")']
        cases = []
        for fork in [False, True]:
            commands += ['set Test / fork := ' + str(fork).lower()]
            for mode in ['normal', control.fault_mode, 'recovery']:
                name = ('fork-' if fork else 'inprocess-') + mode
                cases.append(dict(name=name, fork=fork, mode=mode))
                request = 'observe' + control.command if mode == control.fault_mode else 'testOnly *SuiteC *SuiteD *SuiteE'
                commands += ['prepare' + control.command + ' ' + mode, request, 'capture' + control.command + ' ' + name]
        inputs = [dict(path=str(p), sha256=sha(p)) for p in sorted(build.rglob('*')) if p.is_file()]
        argv = ['direnv', 'exec', str(root), 'sh', '-c', 'exec sbt --server --sbt-version 2.0.9 -java-home "$JDK21" -batch -J-Xmx6G "$@"', label, '-Dizumi.fixture.scala-version=' + scala, '-Dizumi.fixture.version=' + args.artifact_version, '-Dizumi.fixture.audit-root=' + str(build / 'target/body-audit'), '-Dizumi.fixture.captures=' + str(lane / 'cases'), *commands]
        (lane / 'commands.json').write_text(json.dumps(dict(cwd=str(build), argv=argv, inputs=inputs, cases=cases), indent=2) + '\n')
        print(label.upper().replace('-', '_') + '_BATCH_START ' + scala, flush=True)
        with (lane / 'run.log').open('x') as log:
            code = run_process(argv, build, log, timeout_seconds, DELIVERY_SHUTDOWN_GRACE_SECONDS)
        failures = []
        checks = []
        resources = set()
        parents = set()
        children = set()
        if code:
            failures.append('SBT process failed: inspect run.log')
        else:
            checks = [check(lane, row, args, resources, parents, children) for row in cases]
            assert len(parents) == 1
        for row in inputs:
            assert sha(row['path']) == row['sha256']
        result = dict(scala=scala, actualExit=code, **{report_name: expected_report}, failures=failures, checks=checks)
        (lane / 'completion.json').write_text(json.dumps(result, indent=2) + '\n')
        outcomes.append(result)
        print(json.dumps(result), flush=True)
        if code:
            break
    result = dict(exit=0 if len(outcomes) == len(args.scala_version) and all(not r['actualExit'] for r in outcomes) else 1, outcomes=outcomes)
    (out / 'completion.json').write_text(json.dumps(result, indent=2) + '\n')
    raise SystemExit(result['exit'])


FRAMEWORK = r'''package izumi.fixtures.host

import izumi.distage.testkit.protocol.ForkReceiptArguments
import sbt.testing.{EventHandler, Fingerprint, Framework, Logger, Runner, Task, TaskDef}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths, StandardOpenOption}

final class @FRAMEWORK@ extends Framework {
  private val delegate = new izumi.distage.testkit.runner.bootstrap.Framework
  override def name(): String = delegate.name()
  override def fingerprints(): Array[Fingerprint] = delegate.fingerprints()
  override def runner(args: Array[String], remote: Array[String], loader: ClassLoader): Runner = {
    val invocation = ForkReceiptArguments.parse(args.toVector, remote.toVector)
    val original = delegate.runner(args, remote, loader)
    val audit = Paths.get(sys.props("izumi.fixture.audit-root"))
    new Runner {
      override def args(): Array[String] = original.args()
      override def remoteArgs(): Array[String] = original.remoteArgs()
      override def tasks(definitions: Array[TaskDef]): Array[Task] = {
        val names = definitions.map(_.fullyQualifiedName()).sorted
        val data = (Vector(ProcessHandle.current().pid().toString) ++ names.toVector).mkString("\n")
        val _ = Files.write(audit.resolve("target.selected"), data.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
@TASKS@
      }
      override def done(): String = {
        val result = original.done()
        invocation.hostDirectory.foreach { directory =>
          val entries = Files.list(directory)
          try entries.forEach { path =>
            if (path.getFileName.toString.endsWith(".terminal")) {
              val captured = audit.resolve(path.getFileName)
              if (Files.exists(captured)) require(Files.mismatch(path,captured) == -1L,"Repeated terminal capture differs")
              else { val _ = Files.copy(path,captured); () }
            }
          } finally entries.close()
        }
        result
      }
    }
  }
}
'''

SETTINGS = r'''
lazy val @PROJECT@ = project.in(file(".")).enablePlugins(izumi.distage.sbt.DistageTestkitPlugin)
val proxyFramework = new TestFramework("izumi.fixtures.host.@FRAMEWORK@")
Test / testFrameworks := Seq(proxyFramework)
def proxyExecution(value: Tests.Execution): Tests.Execution = value.copy(options = value.options.map {
  case Tests.Argument(Some(framework), arguments) if framework == new TestFramework("izumi.distage.testkit.runner.bootstrap.Framework") => Tests.Argument(proxyFramework, arguments*)
  case other => other
})
Test / testSelected / testExecution := Def.uncached { proxyExecution((Test / testSelected / testExecution).value) }
Test / testQuick / testExecution := Def.uncached { proxyExecution((Test / testQuick / testExecution).value) }
Test / test / testExecution := Def.uncached { proxyExecution((Test / test / testExecution).value) }
Test / testResultLogger := {
  val audit = file(sys.props("izumi.fixture.audit-root"))
  new TestResultLogger {
    override def run(log: sbt.util.Logger, output: Tests.Output, taskName: String): Unit = {
      IO.write(audit / "host.parent",ProcessHandle.current().pid().toString)
      val rows = output.events.toVector.sortBy(_._1).map { case (name,result) => Vector(name,result.passedCount,result.failureCount,result.errorCount).mkString("\t") }
      IO.write(audit / "host.output",rows.mkString("\n"))
      TestResultLogger.Default.run(log,output,taskName)
    }
  }
}
val prepare@COMMAND@ = inputKey[Unit]("Prepare one owned @LABEL@ control")
prepare@COMMAND@ := {
  val parsed = spaceDelimited("mode").parsed
  require(parsed.size == 1 && Set("normal","@FAULT_MODE@","recovery").contains(parsed.head),"Missing @LABEL@ mode")
  val audit = file(sys.props("izumi.fixture.audit-root"))
  IO.delete(audit); IO.createDirectory(audit)
  IO.delete((Test / target).value / "test-reports")
  if (parsed.head == "@FAULT_MODE@") IO.write(audit / "@FAULT_FLAG@","enabled")
}
val observe@COMMAND@ = taskKey[Unit]("Capture a rejected command without leaving the SBT session")
observe@COMMAND@ := Def.uncached {
  val result = (Test / testOnly).toTask(" *SuiteC *SuiteD *SuiteE").result.value
  require(result.toEither.isLeft,"@REJECTION@")
  IO.write(file(sys.props("izumi.fixture.audit-root")) / "host.rejected",result.toEither.left.toOption.get.toString)
}
val capture@COMMAND@ = inputKey[Unit]("Freeze physical bodies and original host reports")
capture@COMMAND@ := {
  val parsed = spaceDelimited("case").parsed
  require(parsed.size == 1,"Missing @LABEL@ case")
  val destination = file(sys.props("izumi.fixture.captures")) / parsed.head
  require(!destination.exists(),"@TITLE@ capture must be new")
  IO.copyDirectory(file(sys.props("izumi.fixture.audit-root")),destination / "audit")
  IO.copyDirectory((Test / target).value / "test-reports",destination / "test-reports")
  val roots = (Test / target).value / "distage-fork-receipts"
  require(!roots.exists() || (roots * "*").get().isEmpty,"@TITLE@ command root survived")
}
'''
