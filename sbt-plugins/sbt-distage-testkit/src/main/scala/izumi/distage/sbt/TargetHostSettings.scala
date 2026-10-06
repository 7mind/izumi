package izumi.distage.sbt

import sbt.*
import sbt.Keys.*

object TargetHostSettings {
  def settings: Seq[Def.Setting[?]] = Seq(
    Test / loadedTestFrameworks := Def.uncached {
      (Test / loadedTestFrameworks).value.map { case (key, framework) =>
        val projected = if (key == DistageHostPolicy.framework) new TargetHostFramework(platform(framework)) else framework
        key -> HostCompletionFramework.wrap(projected)
      }
    },
  )

  private def platform(framework: sbt.testing.Framework): sbt.testing.Framework = framework match {
    case completed: HostCompletionFramework => platform(completed.delegate)
    case other => other
  }
}
