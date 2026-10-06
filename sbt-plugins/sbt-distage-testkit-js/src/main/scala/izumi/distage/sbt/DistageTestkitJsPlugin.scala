package izumi.distage.sbt

import org.scalajs.sbtplugin.ScalaJSPlugin
import sbt.{AutoPlugin, Def, Plugins, PluginTrigger}

object DistageTestkitJsPlugin extends AutoPlugin {
  override def requires: Plugins = ScalaJSPlugin && DistageTestkitPlugin
  override def trigger: PluginTrigger = allRequirements
  override def projectSettings: Seq[Def.Setting[?]] = TargetHostSettings.settings
}
