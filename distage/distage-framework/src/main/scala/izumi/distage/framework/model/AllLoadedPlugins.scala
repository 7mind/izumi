package izumi.distage.framework.model

import izumi.distage.model.definition.{BootstrapModule, Module}
import izumi.distage.plugins.PluginBase

final case class AllLoadedPlugins(app: Seq[PluginBase], bootstrap: Seq[PluginBase] = Nil) {
  def ++(that: AllLoadedPlugins): AllLoadedPlugins = AllLoadedPlugins(app ++ that.app, bootstrap ++ that.bootstrap)
  def ++(appModule: Module): AllLoadedPlugins = ++(AllLoadedPlugins(Seq(appModule.morph[PluginBase]), Nil))
  def ++(bootstrapModule: BootstrapModule): AllLoadedPlugins = ++(AllLoadedPlugins(Nil, Seq(bootstrapModule.morph[PluginBase])))

  def overridenBy(that: AllLoadedPlugins): AllLoadedPlugins = AllLoadedPlugins(Seq(app.merge overridenBy that.app.merge), Seq(bootstrap.merge overridenBy that.bootstrap.merge))
  def overridenBy(appModule: Module): AllLoadedPlugins = overridenBy(AllLoadedPlugins(Seq(appModule.morph[PluginBase]), Nil))
  def overridenBy(bootstrapModule: BootstrapModule): AllLoadedPlugins = overridenBy(AllLoadedPlugins(Nil, Seq(bootstrapModule.morph[PluginBase])))
}

object AllLoadedPlugins {
  def empty: AllLoadedPlugins = AllLoadedPlugins(Nil, Nil)
}
