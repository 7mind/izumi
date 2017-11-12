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
          override def customSettings: Map[SettingsGroupId, ProjectSettings] = original.customSettings

          override def globalSettings: ProjectSettings = original.globalSettings
            .copy(moreExtenders = {
              s =>
                original.globalSettings.moreExtenders(s) ++ extenders
            })
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
    val projectSettings = settings.globalSettingsGroup
    instance
      .withTransformedSettings(_ => settings)
  }

  implicit class ProjectExtensions(project: Project) {
    def registered: Project = {
      getInstance.allProjects += project
      project
    }

    def customSettings(groupId: SettingsGroupId): Project = {
      project
        .settings(getInstance.globalSettings.settingsGroup(groupId).settings: _*)
    }

    def globalSettings: Project = {
      customSettings(SettingsGroupId.GlobalSettingsGroup)
    }

    def defaultRoot: Project = {
      project.extend(SettingsGroupId.RootSettingsGroup)
    }

    def withIt: Project = {
      project.extend(SettingsGroupId.ItSettingsGroup)
    }

    def extend: Project = {
      extend(SettingsGroupId.GlobalSettingsGroup)
    }

    def extend(groupId: SettingsGroupId): Project = {
      val settings = getInstance.globalSettings.settingsGroup(groupId)
      val extenders = settings.extenders
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
    private def project = Project(name, new File(s"$base/$name"))

    private def dirProject = Project(name, new File(base))

    def configured: Project = project.globalSettings

    def module: Project = project.extend.registered

    def root: Project = dirProject.defaultRoot
  }

  class In(val directory: String) {
    def as: WithBase = macro ExtendedProjectMacro.projectUnifiedDslMacro
  }

  object In {
    def apply(directory: String): In = new In(directory)
  }

}

