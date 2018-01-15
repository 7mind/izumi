package org.bitbucket.pshirshov.izumi.sbt.definitions

import org.bitbucket.pshirshov.izumi.sbt.IzumiDslPlugin
import org.bitbucket.pshirshov.izumi.sbt.IzumiScopesPlugin.ProjectReferenceEx
import org.bitbucket.pshirshov.izumi.sbt.IzumiSettingsGroups.autoImport.SettingsGroupId
import sbt._
import sbt.internal.util.ConsoleLogger

import scala.collection.mutable

trait IzumiDsl {
  protected val logger: ConsoleLogger = ConsoleLogger()

  protected[izumi] val allProjects: mutable.HashSet[ProjectReference] = scala.collection.mutable.HashSet[ProjectReference]()

  protected[izumi] def globalSettings: GlobalSettings

  override def toString: String = super.toString + s"[$allProjects]"

  protected def setup(): Unit = {
    IzumiDslPlugin.instance = this
  }

  def withSharedLibs(libs: ProjectReferenceEx*): IzumiDsl = {
    withExtenders(Set(new SharedModulesExtender(libs.toSet)))
  }

  def withExtenders(extenders: Set[Extender]): IzumiDsl = {
    withTransformedSettings {
      original =>
        new GlobalSettings {
          override def settings: Map[SettingsGroupId, ProjectSettings] = {
            val originalSettings = original.allSettings
            val originalGlobals = originalSettings(SettingsGroupId.GlobalSettingsGroup)
            originalSettings.updated(SettingsGroupId.GlobalSettingsGroup, originalGlobals.copy(moreExtenders = {
              case (self@_, existing) =>
                originalGlobals.extenders ++ existing ++ extenders
            }))
          }

        }
    }
  }

  def withTransformedSettings(transform: GlobalSettings => GlobalSettings): IzumiDsl = {
    val settings = globalSettings

    val copy = new IzumiDsl {
      override protected[izumi] def globalSettings: GlobalSettings = transform(settings)
    }

    copy.allProjects ++= this.allProjects
    copy.setup()
    copy
  }

  def allRefs: Seq[ProjectReference] = {
    allProjects.toSeq
  }
}

object IzumiDsl {

}

