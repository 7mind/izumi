package org.bitbucket.pshirshov.izumi.sbt

import org.bitbucket.pshirshov.izumi.sbt.IzumiSettingsGroups.autoImport.SettingsGroupId
import org.bitbucket.pshirshov.izumi.sbt.definitions.{GlobalSettings, IzumiDsl}
import sbt.internal.util.ConsoleLogger
import sbt.io.syntax.File
import sbt.{AutoPlugin, ExtendedProjectMacro, Plugins, Project, ProjectReference}

import scala.language.experimental.macros

object IzumiDslPlugin extends AutoPlugin {
  private val logger: ConsoleLogger = ConsoleLogger()


  override def requires: Plugins = super.requires &&
    IzumiScopesPlugin &&
    IzumiSettingsGroups


  protected[izumi] var instance: IzumiDsl = new IzumiDsl {
    override protected[izumi] def globalSettings: GlobalSettings = new GlobalSettings {}
  }

  //noinspection TypeAnnotation
  object autoImport {


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

}
