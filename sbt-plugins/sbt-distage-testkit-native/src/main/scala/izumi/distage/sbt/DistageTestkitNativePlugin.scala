package izumi.distage.sbt

import sbt.{AutoPlugin, Def, Plugins, PluginTrigger}
import scala.scalanative.sbtplugin.ScalaNativePlugin

object DistageTestkitNativePlugin extends AutoPlugin {
  override def requires: Plugins = ScalaNativePlugin && DistageTestkitPlugin
  override def trigger: PluginTrigger = allRequirements
  override def projectSettings: Seq[Def.Setting[?]] = TargetHostSettings.settings
}
