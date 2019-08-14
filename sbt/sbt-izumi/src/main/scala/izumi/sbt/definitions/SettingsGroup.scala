package izumi.sbt.definitions

import izumi.sbt.plugins.IzumiInheritedTestScopesPlugin.autoImport._
import izumi.sbt.plugins.optional.{IzumiCompilerOptionsPlugin, IzumiExposedTestScopesPlugin, IzumiPublishingPlugin}
import sbt.Keys._
import sbt.librarymanagement.{InclExclRule, ModuleID}
import sbt.{AutoPlugin, Plugins, Project}
import sbtcrossproject.CrossProject

trait SettingsGroupId {
  def name: String

  override def toString: String = s"$name:${hashCode()}"
}

object SettingsGroupId {
  def apply(n: String): SettingsGroupId = new SettingsGroupId() {
    override def name: String = n
  }

  case object ItSettingsGroup extends SettingsGroupId {
    override def name: String = "it"
  }

}

trait AbstractSettingsGroup {
  def id: SettingsGroupId

  def settings: Seq[sbt.Setting[_]] = Seq.empty

  def sharedDeps: Set[ModuleID] = Set.empty

  def sharedLibs: Seq[ProjectReferenceEx] = Seq.empty

  def exclusions: Set[InclExclRule] = Set.empty

  def plugins: Set[Plugins] = Set.empty

  def pluginsJvmOnly: Set[Plugins] = Set.empty

  def disabledPlugins: Set[AutoPlugin] = Set.empty

  def applyTo(p: Project): Project = {
    p
      .enablePlugins(plugins.toSeq: _*)
      .enablePlugins(pluginsJvmOnly.toSeq: _*)
      .disablePlugins(disabledPlugins.toSeq: _*)
      .depends(sharedLibs: _*)
      .settings(
        libraryDependencies ++= sharedDeps.toSeq
        , excludeDependencies ++= exclusions.toSeq
      )
      .settings(settings: _*)
  }

  def applyTo(p: CrossProject): CrossProject = {
    p
      .enablePlugins(plugins.toSeq: _*)
      .disablePlugins(disabledPlugins.toSeq: _*)
      .configurePlatform(sbtcrossproject.JVMPlatform)(p => p.enablePlugins(pluginsJvmOnly.toSeq :_*))
      .dependsSeq(sharedLibs)
      .settings(
        libraryDependencies ++= sharedDeps.toSeq
        , excludeDependencies ++= exclusions.toSeq
      )
      .settings(settings: _*)
  }

  def toImpl: SettingsGroupImpl = SettingsGroupImpl(
    id = id
    , settings = settings
    , sharedDeps = sharedDeps
    , exclusions = exclusions
    , plugins = plugins
    , disabledPlugins = disabledPlugins
    , sharedLibs = sharedLibs
  )
}

case class SettingsGroupImpl
(
  id: SettingsGroupId
  , override val settings: Seq[sbt.Setting[_]] = Seq.empty
  , override val sharedDeps: Set[ModuleID] = Set.empty
  , override val exclusions: Set[InclExclRule] = Set.empty
  , override val plugins: Set[Plugins] = Set.empty
  , override val disabledPlugins: Set[AutoPlugin] = Set.empty
  , override val sharedLibs: Seq[ProjectReferenceEx] = Seq.empty
) extends AbstractSettingsGroup

object SettingsGroupImpl {
  def empty(id: SettingsGroupId): AbstractSettingsGroup = SettingsGroupImpl(id)
}

trait SettingsGroup extends AbstractSettingsGroup {
  override def id: SettingsGroupId = SettingsGroupId(getClass.getName)
}

trait DefaultGlobalSettingsGroup extends SettingsGroup {

  override def pluginsJvmOnly: Set[Plugins] = Set(
    IzumiExposedTestScopesPlugin,
  )

  override def plugins: Set[Plugins] = Set(
    IzumiCompilerOptionsPlugin,
    IzumiPublishingPlugin,
  )
}
