package izumi.distage.plugins

import scala.collection.immutable.Queue

final case class PluginConfig(ops: Seq[PluginOp]) {
  def loadPackages(pkgs: Seq[String]): PluginConfig = add(PluginOp.Load.cached(pkgs))
  def loadPackage(pkg: String): PluginConfig = add(PluginOp.Load.cached(Seq(pkg)))

  def ++(plugins: Seq[PluginBase]): PluginConfig = add(PluginOp.Add(plugins))
  def ++(plugin: PluginBase): PluginConfig = add(PluginOp.Add(Seq(plugin)))

  def overridenBy(plugins: Seq[PluginBase]): PluginConfig = add(PluginOp.Override(plugins))
  def overridenBy(plugin: PluginBase): PluginConfig = add(PluginOp.Override(Seq(plugin)))

  def add(op: PluginOp): PluginConfig = copy(ops = ops :+ op)
}

object PluginConfig {
  /** Scan the specified packages for modules that inherit [[PluginBase]] */
  def cached(packagesEnabled: Seq[String]): PluginConfig = PluginConfig(Queue(PluginOp.Load.cached(packagesEnabled)))
  def packages(packagesEnabled: Seq[String]): PluginConfig = PluginConfig(Queue(PluginOp.Load.packages(packagesEnabled)))

  /** Create a [[PluginConfig]] that simply returns the specified plugins */
  def const(plugins: Seq[PluginBase]): PluginConfig = PluginConfig(Queue(PluginOp.Add(plugins)))
  def const(plugin: PluginBase): PluginConfig = const(Seq(plugin))
  lazy val empty: PluginConfig = const(Nil)
}

sealed trait PluginOp
object PluginOp {
  final case class Load(
                         packagesEnabled: Seq[String],
                         packagesDisabled: Seq[String],
                         cachePackages: Boolean,
                         debug: Boolean,
                       ) extends PluginOp
  object Load {
    def cached(packagesEnabled: Seq[String]): Load = Load(packagesEnabled, Nil, cachePackages = cacheEnabled, debug = false)
    def packages(packagesEnabled: Seq[String]): Load = Load(packagesEnabled, Nil, cachePackages = false, debug = false)

    private[this] lazy val cacheEnabled: Boolean = {
      import izumi.fundamentals.platform.strings.IzString._
      System
        .getProperty(DebugProperties.`izumi.distage.plugins.cache`)
        .asBoolean(true)
    }
  }
  final case class Add(plugins: Seq[PluginBase]) extends PluginOp
  final case class Override(plugins: Seq[PluginBase]) extends PluginOp
}
