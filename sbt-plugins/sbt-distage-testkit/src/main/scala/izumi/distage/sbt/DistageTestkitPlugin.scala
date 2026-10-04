package izumi.distage.sbt

import sbt.{AutoPlugin, Def, Test, inConfig, inputKey, settingKey, taskKey}
import sbt.plugins.JvmPlugin

object DistageTestkitPlugin extends AutoPlugin {
  override def requires: AutoPlugin = JvmPlugin

  object autoImport {
    val distageBuildId = settingKey[String]("Distage catalogue build identity")
    val distageTargetId = settingKey[String]("Distage catalogue configuration identity")
    val distageCatalogueId = taskKey[String]("Identity of the discovered distage suite set")
    val distageList = inputKey[Unit]("List resolved distage test identities and effective settings without executing tests")
    val distagePlan = inputKey[Unit]("Inspect the selected distage dependency plans without provisioning resources or executing tests")

    def distageTestSettings: Seq[Def.Setting[?]] = HostSettings.settings
  }

  override def projectSettings: Seq[Def.Setting[?]] = inConfig(Test)(autoImport.distageTestSettings)
}
