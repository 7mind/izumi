package izumi.distage.sbt

import sbt.{AutoPlugin, Def, Test, inConfig, settingKey, taskKey}
import sbt.plugins.JvmPlugin

object DistageTestkitPlugin extends AutoPlugin {
  override def requires: AutoPlugin = JvmPlugin

  object autoImport {
    val distageBuildId = settingKey[String]("Distage catalogue build identity")
    val distageTargetId = settingKey[String]("Distage catalogue configuration identity")
    val distageCatalogueId = taskKey[String]("Identity of the discovered distage suite set")

    def distageTestSettings: Seq[Def.Setting[?]] = HostSettings.settings
  }

  override def projectSettings: Seq[Def.Setting[?]] = inConfig(Test)(autoImport.distageTestSettings)
}
