package com.github.pshirshov.izumi.sbt

import com.github.pshirshov.izumi.sbt.IzumiSettingsGroups.autoImport.SettingsGroupId
import com.github.pshirshov.izumi.sbt.definitions.{GlobalSettings, IzumiDsl}
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

      def settings(groupIds: SettingsGroupId*): Project = {
        val extenders = groupIds.flatMap {
          groupId =>
            val groupSettings = getInstance.globalSettings.allSettings(groupId)
            groupSettings.extenders
        }
        
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

    class WithBase(name: String, base: String, settingsGroups: Seq[SettingsGroupId]) {
      private def dirProject = Project(name, new File(base))

      private def moduleProject = Project(name, new File(s"$base/$name"))

      def just: Project = moduleProject

      def project: Project = moduleProject.remember

      def root: Project = dirProject.rootSettings

      def module: Project = moduleProject.globalSettings.settings(settingsGroups: _*).remember
    }

    class In(val directory: String, val settingsGroups: Seq[SettingsGroupId]) {
      def withModuleSettings(groupId: SettingsGroupId) = {
        new In(directory, settingsGroups :+ groupId)
      }

      def as: WithBase = macro ExtendedProjectMacro.projectUnifiedDslMacro
    }

    object In {
      def apply(directory: String): In = new In(directory, Seq.empty)
    }

  }

}
