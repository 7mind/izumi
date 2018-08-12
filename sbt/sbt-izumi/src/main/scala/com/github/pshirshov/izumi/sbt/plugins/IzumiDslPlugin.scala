package com.github.pshirshov.izumi.sbt.plugins

import com.github.pshirshov.izumi.sbt.definitions._
import sbt.internal.util.ConsoleLogger
import sbt.io.syntax.File
import sbt.{AutoPlugin, Def, ExtendedProjectMacro, Plugins, Project, ProjectReference}

import scala.collection.mutable
import scala.language.experimental.macros

object IzumiDslPlugin extends AutoPlugin {
  private val logger: ConsoleLogger = ConsoleLogger()

  protected[izumi] val allProjects: mutable.HashSet[ProjectReference] = scala.collection.mutable.HashSet[ProjectReference]()


  override def requires: Plugins = super.requires && IzumiInheritedTestScopesPlugin

  //noinspection TypeAnnotation
  object autoImport {

    implicit class ProjectExtensions(project: Project) {
      def remember: Project = {
        allProjects += project
        project
      }

      def settingsSeq(groups: Seq[AbstractSettingsGroup]) = {
        groups.distinct.foldLeft(project) {
          case (acc, g) =>
            g.applyTo(acc)
        }
      }

      def settings(groups: AbstractSettingsGroup*): Project = {
        settingsSeq(groups)
      }

      def transitiveAggregateSeq(refs: Seq[ProjectReference]): Project = {
        import sbt.Keys._
        logger.info(s"Project ${project.id} is aggregating ${refs.size} projects and ${allProjects.size} transitive projects...")
        val toAggregate = refs ++ allProjects

        project
          .aggregate(toAggregate: _*)
          .settings(compile in sbt.Compile := Def.taskDyn {
            val ctask = (compile in sbt.Compile).value

            Def.task {
              val loadedReferences = loadedBuild.value.allProjectRefs.map(_._1.project).toSet

              val knownReferences = toAggregate.collect {
                case l: sbt.LocalProject =>
                  l.project
                case p: sbt.ProjectRef =>
                  p.project
              }.toSet ++ Set(project.id)

              val notAggregated = loadedReferences.diff(knownReferences)

              if (notAggregated.nonEmpty) {
                logger.warn("!!! WARNING !!! WARNING !!! WARNING !!! ")
                logger.warn(s"The following projects are loaded but not aggregated by `${project.id}` project:\n${notAggregated.mkString("\n")}")
              }


              ctask
            }
          }.value)

      }

      def transitiveAggregate(refs: ProjectReference*): Project = {
        transitiveAggregateSeq(refs)
      }
    }

    class WithBase(name: String, base: String, settingsGroups: Seq[AbstractSettingsGroup]) {
      private def dirProject = Project(name, new File(base))

      private def moduleProject = Project(name, new File(s"$base/$name"))

      def just: Project = {
        moduleProject
      }

      def root: Project = {
        new ProjectExtensions(dirProject).settings(settingsGroups.distinct: _*)
      }

      def project: Project = {
        moduleProject.remember
      }

      def module: Project = {
        new ProjectExtensions(moduleProject).settings(settingsGroups.distinct: _*).remember
      }
    }

    class In(val directory: String, val settingsGroups: Seq[AbstractSettingsGroup]) {
      def settingsSeq(groups: Seq[AbstractSettingsGroup]) = {
        new In(directory, settingsGroups ++ groups)
      }

      def settings(groups: AbstractSettingsGroup*) = {
        settingsSeq(groups)
      }

      def as: WithBase = macro ExtendedProjectMacro.projectUnifiedDslMacro
    }

    object In {
      def apply(directory: String): In = new In(directory, Seq.empty)
    }

  }

}
