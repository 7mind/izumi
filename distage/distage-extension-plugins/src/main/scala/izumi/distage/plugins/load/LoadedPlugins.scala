package izumi.distage.plugins.load

import izumi.distage.plugins.PluginBase

final case class LoadedPlugins(
  loaded: Seq[PluginBase],
  merges: Seq[PluginBase],
  overrides: Seq[PluginBase],
) {
  def result: Seq[PluginBase] = {
    val merged = loaded ++ merges
    if (overrides.nonEmpty) {
      Seq((merged.merge +: overrides).overrideLeft)
    } else merged
  }
  def allRaw: Seq[PluginBase] = (loaded.iterator ++ merges.iterator ++ overrides.iterator).toSeq
  def size: Int = loaded.size + merges.size + overrides.size
  def ++(that: LoadedPlugins): LoadedPlugins = LoadedPlugins(this.loaded ++ that.loaded, this.merges ++ that.merges, this.overrides ++ that.overrides)
}

object LoadedPlugins {
  def const(merges: Seq[PluginBase]): LoadedPlugins = LoadedPlugins(Nil, merges, Nil)

  def empty: LoadedPlugins = LoadedPlugins(Nil, Nil, Nil)
}
