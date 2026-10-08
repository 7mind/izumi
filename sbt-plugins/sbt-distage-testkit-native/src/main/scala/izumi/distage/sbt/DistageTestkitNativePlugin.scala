package izumi.distage.sbt

import sbt.{AutoPlugin, Def, Plugins, PluginTrigger, Test, inConfig}
import scala.scalanative.sbtplugin.{ScalaNativePlugin, ScalaNativePluginInternal}

object DistageTestkitNativePlugin extends AutoPlugin {
  object autoImport {
    def distageNativeTestSettings: Seq[Def.Setting[?]] =
      ScalaNativePluginInternal.scalaNativeTestSettings ++ DistageTestkitPlugin.autoImport.distageTestSettings ++ TargetHostSettings.settings
  }

  override def requires: Plugins = ScalaNativePlugin && DistageTestkitPlugin
  override def trigger: PluginTrigger = allRequirements
  override def projectSettings: Seq[Def.Setting[?]] = inConfig(Test)(TargetHostSettings.settings)
}
