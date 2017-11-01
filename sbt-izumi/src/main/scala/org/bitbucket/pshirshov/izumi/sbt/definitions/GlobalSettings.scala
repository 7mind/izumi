package org.bitbucket.pshirshov.izumi.sbt.definitions

import org.bitbucket.pshirshov.izumi.sbt._
import sbt.librarymanagement.InclExclRule
import sbt.{ModuleID, Plugins}


trait GlobalSettings {
  def globalExclusions: Set[InclExclRule] = Set.empty

  def globalSettings: Seq[sbt.Setting[_]] = Seq.empty

  def sharedDeps: Set[ModuleID] = Set.empty

  def rootPlugins: Set[Plugins] = Set(
    BuildPlugin
    , CompilerOptionsPlugin
    , PublishingPlugin
    , ResolverPlugin
    , TestingPlugin
  )
}
