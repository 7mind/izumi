package izumi.distage.sbt

import sbt.*
import sbt.Keys.*
import sbt.complete.DefaultParsers.spaceDelimited
import izumi.distage.sbt.DistageTestkitPlugin.autoImport.*
import izumi.distage.testkit.protocol.{RequestArguments, RequestOperation}
import sbt.testing.{SuiteSelector, TaskDef}

object TargetHostSettings {
  def settings: Seq[Def.Setting[?]] = Seq(
    Test / loadedTestFrameworks := Def.uncached {
      (Test / loadedTestFrameworks).value.map { case (key, framework) =>
        val projected = if (key == DistageHostPolicy.framework) new TargetHostFramework(platform(framework)) else framework
        key -> HostCompletionFramework.wrap(projected)
      }
    },
    Test / distageList := {
      val options = spaceDelimited("distage request options").parsed
      Def.uncached {
        val identities = DistageHostPolicy.arguments((Test / distageBuildId).value, (Test / distageTargetId).value, (Test / distageCatalogueId).value)
        val request = RequestArguments.parse((identities ++ options).toVector).fold(error => throw new IllegalArgumentException(error.message), identity)
        val definitions = (Test / definedTests).value.filter(DistageHostPolicy.isDistage).map(definition => new TaskDef(definition.name, definition.fingerprint, false, Array(new SuiteSelector))).toVector
        val framework = platform((Test / loadedTestFrameworks).value(DistageHostPolicy.framework))
        val log = streams.value.log
        TargetHostInspection.run(framework, definitions, request, RequestOperation.Resolve, log.info(_))
      }
    },
    Test / distagePlan := {
      val options = spaceDelimited("distage request options").parsed
      Def.uncached {
        val identities = DistageHostPolicy.arguments((Test / distageBuildId).value, (Test / distageTargetId).value, (Test / distageCatalogueId).value)
        val request = RequestArguments.parse((identities ++ options).toVector).fold(error => throw new IllegalArgumentException(error.message), identity)
        val definitions = (Test / definedTests).value.filter(DistageHostPolicy.isDistage).map(definition => new TaskDef(definition.name, definition.fingerprint, false, Array(new SuiteSelector))).toVector
        val framework = platform((Test / loadedTestFrameworks).value(DistageHostPolicy.framework))
        val log = streams.value.log
        TargetHostInspection.run(framework, definitions, request, RequestOperation.Plan, log.info(_))
      }
    },
  )

  private def platform(framework: sbt.testing.Framework): sbt.testing.Framework = framework match {
    case completed: HostCompletionFramework => platform(completed.delegate)
    case projected: TargetHostFramework => platform(projected.platform)
    case other => other
  }
}
