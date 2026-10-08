from pathlib import Path
import importlib.util
import json

import sys
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from fixture_harness import write_sbt_project, freeze_driver, run_process, sha, scala_consumer_parser

TIMEOUT_SECONDS = 240
BUILD = r'''
lazy val commonSettings = Seq(
  scalaVersion := sys.props("fixture.scala-version"),
  libraryDependencies += "io.7mind.izumi" %% "distage-test-runner" % sys.props("fixture.artifact-version") % Test,
  scalacOptions ++= { if (scalaVersion.value.startsWith("3.")) Seq("-release:17", "-Ybackend-parallelism", "1") else Seq("-release:17") },
  Test / unmanagedSourceDirectories := Seq(file(sys.props("fixture.sources"))),
  Test / testFrameworks := Seq(new TestFramework("fixture.GroupFramework")),
  Test / fork := false
)
lazy val stock = project.in(file("stock")).settings(commonSettings).settings(inConfig(Test)(EarlyInputs.settings))
lazy val adapted = project.in(file("adapted")).settings(commonSettings)
  .settings(inConfig(Test)(EarlyInputs.settings ++ izumi.distage.sbt.DistageTestkitPlugin.autoImport.distageTestSettings))
lazy val inputConsumer = project.in(file("."))
val prepareHook = inputKey[Unit]("Prepare one input-composition control")
prepareHook := {
  val parsed = sbt.complete.DefaultParsers.spaceDelimited("case").parsed
  require(parsed.size == 1, "Missing control name")
  val audit = file(sys.props("fixture.audit-root"))
  IO.delete(audit); IO.createDirectory(audit)
  IO.write(audit / "mode", "single")
}
val verifyHook = inputKey[Unit]("Verify the inherited input customization executes once")
verifyHook := {
  val parsed = sbt.complete.DefaultParsers.spaceDelimited("case").parsed
  require(parsed.size == 1, "Missing control name")
  val audit = file(sys.props("fixture.audit-root"))
  val capture = file(sys.props("fixture.captures")) / parsed.head
  require(!capture.exists(), "Capture must be new")
  IO.copyDirectory(audit, capture)
  val hook = audit / "input.hook"
  require(hook.exists(), "EARLY_INPUT_HOOK_MISSING case=" + parsed.head)
  val command = parsed.head.split("-", 2).last
  require(IO.readLines(hook) == Seq(command, "composed:" + command), "EARLY_INPUT_HOOK_COUNT_DIFFERENT")
  require((audit * "*.body").get().size == 3, "EARLY_INPUT_BODY_COUNT_DIFFERENT")
  streams.value.log.info("EARLY_INPUT_COMPOSITION_OK case=" + parsed.head)
}
'''
EARLY_INPUTS = r'''
import sbt.*
import sbt.Keys.*
object EarlyInputs {
  def settings: Seq[Def.Setting[?]] = Seq(testOnly, testSelected, testQuick).flatMap { key =>
    Seq(key.set0(Def.setting { sys.error("OVERWRITTEN_INPUT_EVALUATED") }, NoPosition), wrap(key), compose(key))
  }
  private def wrap(key: InputKey[TestResult]): Def.Setting[InputTask[TestResult]] = {
    val definitions = Defaults.testTasks.filter(_.key.key == key.key)
    require(definitions.size == 1, "Stock input definition must be unique")
    val definition = definitions.head.asInstanceOf[Def.Setting[InputTask[TestResult]]]
    key.set0(Def.setting {
      val inherited = definition.init.value
      val audit = file(sys.props("fixture.audit-root"))
      inherited.mapTask(task => task.map { result =>
        IO.append(audit / "input.hook", key.key.label + "\n")
        result
      })
    }, NoPosition)
  }
  private def compose(key: InputKey[TestResult]): Def.Setting[InputTask[TestResult]] = {
    val previous = Scoped.scopedSetting(key.scope, key.key)
    key.set0(Def.setting {
      val inherited = previous.value
      val audit = file(sys.props("fixture.audit-root"))
      inherited.mapTask(task => task.map { result =>
        IO.append(audit / "input.hook", "composed:" + key.key.label + "\n")
        result
      })
    }, NoPosition)
  }
}
'''


def main():
    parser = scala_consumer_parser(multiple=False)
    parser.add_argument('--plugin-version', required=True)
    args = parser.parse_args()
    out = args.evidence_dir.resolve()
    out.mkdir()
    freeze_driver(__file__, out / 'driver.py')
    framework_driver = Path(__file__).with_name('verify-command-groups.py')
    spec = importlib.util.spec_from_file_location('foreign_groups', framework_driver)
    assert spec is not None and spec.loader is not None
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    build = out / 'build'
    source = build / 'src/test/scala/GroupFramework.scala'
    source.parent.mkdir(parents=True)
    source.write_text(module.SOURCE)
    write_sbt_project(build, BUILD, '2.0.9', 'addSbtPlugin("io.7mind.izumi" % "sbt-distage-testkit" % "' + args.plugin_version + '")\n')
    (build / 'project/EarlyInputs.scala').write_text(EARLY_INPUTS)
    inputs = [dict(path=str(path), sha256=sha(path)) for path in sorted(build.rglob('*')) if path.is_file()]
    inputs += [dict(path=str(path), sha256=sha(path)) for path in [Path(__file__), framework_driver]]
    commands = ['set Global / localCacheDirectory := file("' + str(out / 'local-cache') + '")']
    for name in ['stock', 'adapted']:
        for key in ['testQuick', 'testSelected', 'testOnly']:
            case = name + '-' + key
            commands += ['prepareHook ' + case, name + ' / Test / ' + key + ' fixture.SuiteA', 'verifyHook ' + case]
    argv = ['direnv', 'exec', str(args.repo_root.resolve()), 'sh', '-c', 'exec sbt --server --sbt-version 2.0.9 -java-home "$JDK21" -batch -J-Xmx6G "$@"', 'input-composition', '-Dfixture.scala-version=' + args.scala_version, '-Dfixture.artifact-version=' + args.artifact_version, '-Dfixture.sources=' + str(source.parent), '-Dfixture.audit-root=' + str(build / 'audit'), '-Dfixture.captures=' + str(out / 'cases'), *commands]
    (out / 'command.json').write_text(json.dumps(dict(cwd=str(build), argv=argv, inputs=inputs), indent=2) + '\n')
    with (out / 'run.log').open('x') as log:
        actual = run_process(argv, build, log, TIMEOUT_SECONDS, 10)
    for row in inputs:
        assert sha(Path(row['path'])) == row['sha256']
    result = dict(exit=actual, scala=args.scala_version, inputsUnchanged=True)
    (out / 'completion.json').write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps(result), flush=True)
    raise SystemExit(actual)


if __name__ == '__main__':
    main()
