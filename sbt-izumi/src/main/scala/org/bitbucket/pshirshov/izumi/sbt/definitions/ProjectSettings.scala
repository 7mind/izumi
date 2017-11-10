package org.bitbucket.pshirshov.izumi.sbt.definitions

import sbt.ModuleID
import sbt.librarymanagement.InclExclRule

import scala.collection.mutable

trait ProjectSettings {
  def exclusions: Set[InclExclRule] = Set.empty

  def settings: Seq[sbt.Setting[_]] = Seq.empty

  def sharedDeps: Set[ModuleID] = Set.empty

//  def extenders: Set[Extender] = Set.empty
}

object ProjectSettings {
  def empty: ProjectSettings = new ProjectSettings {}
}
