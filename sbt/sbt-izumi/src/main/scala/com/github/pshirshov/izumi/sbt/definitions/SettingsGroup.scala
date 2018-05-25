package com.github.pshirshov.izumi.sbt.definitions

import com.github.pshirshov.izumi.sbt.IzumiScopesPlugin.autoImport._
import com.github.pshirshov.izumi.sbt.IzumiSettingsGroups.autoImport.SettingsGroupId
import sbt.Keys._
import sbt.librarymanagement.{InclExclRule, ModuleID}
import sbt.{AutoPlugin, Plugins, Project}

trait AbstractSettingsGroup {
  def id: SettingsGroupId

  def settings: Seq[sbt.Setting[_]] = Seq.empty

  def sharedDeps: Set[ModuleID] = Set.empty

  def sharedLibs: Seq[ProjectReferenceEx] = Seq.empty

  def exclusions: Set[InclExclRule] = Set.empty

  def plugins: Set[Plugins] = Set.empty

  def disabledPlugins: Set[AutoPlugin] = Set.empty

  def applyTo(p: Project): Project = {
    p
      .enablePlugins(plugins.toSeq: _*)
      .disablePlugins(disabledPlugins.toSeq: _*)
      .depends(sharedLibs :_*)
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
