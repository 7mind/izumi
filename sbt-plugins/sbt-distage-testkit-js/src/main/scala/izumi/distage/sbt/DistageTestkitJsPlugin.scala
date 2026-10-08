package izumi.distage.sbt

import org.scalajs.sbtplugin.ScalaJSPlugin
import sbt.{AutoPlugin, Def, Plugins, PluginTrigger, Test, inConfig}

object DistageTestkitJsPlugin extends AutoPlugin {
  object autoImport {
    def distageJsTestSettings: Seq[Def.Setting[?]] =
      ScalaJSPlugin.testConfigSettings ++ DistageTestkitPlugin.autoImport.distageTestSettings ++ TargetHostSettings.settings
  }

  override def requires: Plugins = ScalaJSPlugin && DistageTestkitPlugin
  override def trigger: PluginTrigger = allRequirements
  override def projectSettings: Seq[Def.Setting[?]] = inConfig(Test)(TargetHostSettings.settings)
}
