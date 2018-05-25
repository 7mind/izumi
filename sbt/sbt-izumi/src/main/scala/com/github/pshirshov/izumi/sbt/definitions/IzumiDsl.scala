package com.github.pshirshov.izumi.sbt.definitions

import com.github.pshirshov.izumi.sbt.IzumiDslPlugin
import com.github.pshirshov.izumi.sbt.IzumiScopesPlugin.ProjectReferenceEx
import com.github.pshirshov.izumi.sbt.IzumiSettingsGroups.autoImport
import com.github.pshirshov.izumi.sbt.IzumiSettingsGroups.autoImport.SettingsGroupId
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
    withTransformedSettings {
      original =>
        val originalSettings = original.allSettings
        val originalGlobals = originalSettings(SettingsGroupId.GlobalSettingsGroup)

        new GlobalSettings {
          override def settings: Map[autoImport.SettingsGroupId, AbstractSettingsGroup] = {
            originalSettings.updated(SettingsGroupId.GlobalSettingsGroup
              , originalGlobals.toImpl.copy(sharedLibs = originalGlobals.sharedLibs ++ libs)
            )
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

