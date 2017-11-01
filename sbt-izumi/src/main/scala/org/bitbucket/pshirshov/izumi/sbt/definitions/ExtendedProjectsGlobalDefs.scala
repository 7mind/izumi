package org.bitbucket.pshirshov.izumi.sbt.definitions

import org.bitbucket.pshirshov.izumi.sbt._
import org.bitbucket.pshirshov.izumi.sbt.definitions.ExtendedProjects.ProjectReferenceEx
import sbt._
import sbt.internal.util.ConsoleLogger
import sbt.librarymanagement.syntax

import scala.collection.mutable
import scala.language.experimental.macros

trait ExtendedProjectsGlobalDefs {
  protected val logger: ConsoleLogger = ConsoleLogger()

  protected val extenders: mutable.HashSet[Extender] = scala.collection.mutable.HashSet[Extender]()
  protected val allProjects: mutable.HashSet[ProjectReference] = scala.collection.mutable.HashSet[ProjectReference]()

  protected def globalSettings: GlobalSettings

  protected def addExtender(e: Extender): Unit = {
    extenders += e
  }

  override def toString: String = super.toString + s" [$allProjects ; $extenders]"

  protected def setup(): Unit = {
    ExtendedProjectsGlobalDefs.instance = this
  }

  def withSharedLibs(libs: ProjectReferenceEx*): ExtendedProjectsGlobalDefs = {
    val copy = new GlobalDefsCopy(this.globalSettings)
    copy.allProjects ++= this.allProjects
    copy.extenders ++= this.extenders
    copy.addExtender(new SharedModulesExtender(libs.toSet))
    copy.setup()
    copy
  }

  def allRefs: Seq[ProjectReference] = {
    allProjects.toSeq
  }
}

class GlobalDefs(override protected val globalSettings: GlobalSettings) extends ExtendedProjectsGlobalDefs {
  protected def init(): Unit = {
    addExtender(new GlobalSettingsExtender(globalSettings))
    addExtender(new SharedDepsExtender(globalSettings))
    addExtender(new GlobalSettingsExtender(globalSettings))
    super.setup()
  }

  init()
}

class GlobalDefsCopy(override protected val globalSettings: GlobalSettings) extends ExtendedProjectsGlobalDefs {
}

object ExtendedProjectsGlobalDefs {
  private val logger: ConsoleLogger = ConsoleLogger()
  private var instance: ExtendedProjectsGlobalDefs = new GlobalDefs(new GlobalSettings {})

  implicit class ProjectExtensions(project: Project) {
    def registered: Project = {
      getInstance.allProjects += project
      project
    }

    def plain(f: String): Project = {
      project
        .in(file(f))
    }

    def from(f: String): Project = {
      plain(f)
        .extend
        .registered
    }

    def root(f: String): Project = {
      plain(f)
        .defaultRoot
    }

    def globalSettings: Project = {
      project
        .settings(getInstance.globalSettings.globalSettings :_*)
    }

    def defaultRoot: Project = {
      project
        .globalSettings
        .enablePlugins(getInstance.globalSettings.rootPlugins.toSeq :_*)
    }

    def withIt: Project = {
      project
        .configs(syntax.IntegrationTest)
        .settings(Defaults.itSettings)
        .settings(NestedTestScopesPlugin.itSettings)
    }

    def transitiveAggregate(refs: ProjectReference*): Project = {
      logger.info(s"Project ${project.id} is aggregating ${refs.size} projects and ${getInstance.allProjects.size} transitive projects...")
      project
        .aggregate(refs ++ getInstance.allProjects :_*)
    }


    def extend: Project = {
      logger.debug(s"Applying ${getInstance.extenders.size} transformers to ${project.id}...")

      getInstance.extenders.foldLeft(project) {
        case (acc, t) =>
          t.extend(acc)
      }
    }

    private def getInstance: ExtendedProjectsGlobalDefs = {
      if (instance == null) {
        val message = s"Cannot extend project ${project.id}: ExtendedProjectsGlobalDefs trait was not instantiated in build"
        logger.error(message)
        throw new IllegalStateException(message)
      }

      logger.debug(s"Defs instance = $instance...")

      instance
    }

  }

  object Module {
    def in(directory: String): Project = macro ExtendedProjectMacro.projectExFullMacroImpl
  }

  object ConfiguredModule {
    def in(directory: String): Project = macro ExtendedProjectMacro.projectExConfiguredMacroImpl
  }

  object RootModule {
    def in(directory: String): Project = macro ExtendedProjectMacro.projectExRootMacroImpl
  }
}

