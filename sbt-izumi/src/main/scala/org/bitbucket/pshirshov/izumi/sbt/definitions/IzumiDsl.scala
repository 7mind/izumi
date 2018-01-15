package org.bitbucket.pshirshov.izumi.sbt.definitions

import org.bitbucket.pshirshov.izumi.sbt.definitions.IzumiScopes.ProjectReferenceEx
import sbt._
import sbt.internal.util.ConsoleLogger

import scala.collection.mutable
import scala.language.experimental.macros

trait IzumiDsl {
  protected val logger: ConsoleLogger = ConsoleLogger()

  protected val allProjects: mutable.HashSet[ProjectReference] = scala.collection.mutable.HashSet[ProjectReference]()

  protected def globalSettings: GlobalSettings

  override def toString: String = super.toString + s"[$allProjects]"

  protected def setup(): Unit = {
    IzumiDsl.instance = this
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
      override protected def globalSettings: GlobalSettings = transform(settings)
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
  private val logger: ConsoleLogger = ConsoleLogger()
  private var instance: IzumiDsl = new IzumiDsl {
    override protected def globalSettings: GlobalSettings = new GlobalSettings {}
  }

  def setup(settings: GlobalSettings): IzumiDsl = {
    instance
      .withTransformedSettings(_ => settings)
  }

  implicit class ProjectExtensions(project: Project) {
    def remember: Project = {
      getInstance.allProjects += project
      project
    }

    def rootSettings: Project = {
      project
        .settings(SettingsGroupId.GlobalSettingsGroup)
        .settings(SettingsGroupId.RootSettingsGroup)
    }

    def itSettings: Project = {
      project
        .settings(SettingsGroupId.ItSettingsGroup)
    }

    def globalSettings: Project = {
      settings(SettingsGroupId.GlobalSettingsGroup)
    }

    def settings(groupId: SettingsGroupId): Project = {
      val groupSettings = getInstance.globalSettings.allSettings(groupId)
      val extenders = groupSettings.extenders
      logger.debug(s"Applying ${extenders.size} transformers to ${project.id}...")

      extenders.foldLeft(project) {
        case (acc, t) =>
          t.extend(acc)
      }
    }

    def transitiveAggregate(refs: ProjectReference*): Project = {
      logger.info(s"Project ${project.id} is aggregating ${refs.size} projects and ${getInstance.allProjects.size} transitive projects...")
      project
        .aggregate(refs ++ getInstance.allProjects: _*)
    }

    private def getInstance: IzumiDsl = {
      if (instance == null) {
        val message = s"Cannot extend project ${project.id}: ExtendedProjectsGlobalDefs trait was not instantiated in build"
        logger.error(message)
        throw new IllegalStateException(message)
      }

      logger.debug(s"Defs instance = $instance...")

      instance
    }
  }

  class WithBase(name: String, base: String) {
    private def moduleProject = Project(name, new File(s"$base/$name"))

    private def dirProject = Project(name, new File(base))

    def project: Project = moduleProject.remember

    def module: Project = moduleProject.globalSettings.remember

    def root: Project = dirProject.rootSettings
  }

  class In(val directory: String) {
    def as: WithBase = macro ExtendedProjectMacro.projectUnifiedDslMacro
  }

  object In {
    def apply(directory: String): In = new In(directory)
  }

}

