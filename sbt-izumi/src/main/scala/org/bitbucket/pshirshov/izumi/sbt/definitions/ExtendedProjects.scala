package org.bitbucket.pshirshov.izumi.sbt.definitions

import sbt.internal.util.ConsoleLogger
import sbt.librarymanagement.syntax
import sbt.{ClasspathDep, Project, ProjectReference}

import scala.language.implicitConversions

object ExtendedProjects {
  sealed trait ProjectReferenceEx
  case class ClasspathRef(ref: ClasspathDep[ProjectReference]) extends ProjectReferenceEx
  case class ImprovedProjectRef(ref: Project) extends ProjectReferenceEx
  case class DefaultProjectRef(ref: Project) extends ProjectReferenceEx

  implicit def toClasspathRef(ref: ClasspathDep[ProjectReference]): ClasspathRef = ClasspathRef(ref)
  implicit def toImprovedProjectRef(ref: Project): ImprovedProjectRef = ImprovedProjectRef(ref)

  private val logger: ConsoleLogger = ConsoleLogger()

  implicit class ProjectReferenceExtensions(project: Project) {
    private val haveIntegrationTests = project.configurations.contains(syntax.IntegrationTest)

    def defaultRef: DefaultProjectRef = DefaultProjectRef(project)

    def testOnlyRef: ClasspathRef = {

      val conf = if (!haveIntegrationTests) {
        "test->compile,test"
      } else {
        "test->compile,test;it->compile,test,it"
      }
      
      ClasspathRef((project: ProjectReference) % conf)
    }

    def depends(deps: ProjectReferenceEx*): Project = {
      val refinedDeps = deps.map {
        case ClasspathRef(ref) =>
          ref

        case ImprovedProjectRef(ref) =>
          val conf = if (!haveIntegrationTests) {
            "test->test;compile->compile"
          } else {
            "test->test;compile->compile;it->it;it->test"
          }

          (ref: ProjectReference) % conf

        case DefaultProjectRef(ref) =>
          (ref: ProjectReference): ClasspathDep[ProjectReference]
      }

      logger.debug(s""" * ${project.id} <-- ${refinedDeps.map(d =>  s"[ ${d.project} ${d.configuration.getOrElse("_")} ]").mkString(", ")}""")

      project.dependsOn(refinedDeps: _*)
    }
  }

}
