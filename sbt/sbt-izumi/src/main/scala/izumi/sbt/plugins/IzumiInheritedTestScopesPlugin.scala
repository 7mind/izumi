package izumi.sbt.plugins

import sbt.internal.util.ConsoleLogger
import sbt.librarymanagement.syntax
import sbt.{AutoPlugin, ClasspathDep, Project, ProjectReference}
import sbtcrossproject._

import scala.language.implicitConversions

object IzumiInheritedTestScopesPlugin extends AutoPlugin {
  sealed trait ProjectReferenceEx

  final case class ClasspathRef(ref: ClasspathDep[ProjectReference]) extends ProjectReferenceEx
  final case class ImprovedProjectRef(ref: Project) extends ProjectReferenceEx
  final case class DefaultProjectRef(ref: Project) extends ProjectReferenceEx

  final case class DefaultProjectXRef(ref: CrossProject) extends ProjectReferenceEx
  final case class ClasspathXRef(ref: CrossClasspathDependency) extends ProjectReferenceEx

  private val logger: ConsoleLogger = ConsoleLogger()


  //noinspection TypeAnnotation
  object autoImport {
    type ProjectReferenceEx = IzumiInheritedTestScopesPlugin.ProjectReferenceEx

    implicit def toClasspathRef(ref: ClasspathDep[ProjectReference]): ClasspathRef = ClasspathRef(ref)
    implicit def toImprovedProjectRef(ref: Project): ImprovedProjectRef = ImprovedProjectRef(ref)
    implicit def toImprovedProjectXRef(ref: CrossProject): DefaultProjectXRef = DefaultProjectXRef(ref)
    implicit def toCrossRef(ref: CrossClasspathDependency): ClasspathXRef = ClasspathXRef(ref)

    implicit class  CrossClasspathDependencyExt(ref: CrossProject) {
      import CrossPlugin.autoImport._
      def testOnlyRef: CrossClasspathDependency = ref % "test->compile,test"
      def testOnlyRefIt: CrossClasspathDependency = ref % "test->compile,test;it->compile,test,it"
      def ets: CrossClasspathDependency = ref %  "test->test;compile->compile"
      def etsIt: CrossClasspathDependency = ref %  "test->test;compile->compile;it->it;it->test"
    }

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

      def dependsSeq(deps: Seq[ProjectReferenceEx]): Project = {
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

          case o =>
            throw new IllegalArgumentException(s"non-CrossProject $project cannot depend on CrossProject $o")
        }

        logger.debug(s""" * ${project.id} <-- ${refinedDeps.map(d =>  s"[ ${d.project} ${d.configuration.getOrElse("_")} ]").mkString(", ")}""")

        project.dependsOn(refinedDeps: _*)
      }

      def depends(deps: ProjectReferenceEx*): Project = {
        dependsSeq(deps)
      }
    }


    implicit class XProjectReferenceExtensions(project: CrossProject) {
      def defaultRef: DefaultProjectXRef = DefaultProjectXRef(project)

      private def extractCrossDeps(refs: Seq[ProjectReferenceEx]): Seq[CrossClasspathDependency] = {
        import sbtcrossproject.CrossPlugin.autoImport._

        refs.collect {
          case d: ClasspathXRef => d.ref
          case d: DefaultProjectXRef => d.ref : CrossClasspathDependency
          case o =>
            throw new IllegalArgumentException(s"CrossProject $project cannot depend on non-CrossProject $o")
        }
      }

      def dependsSeq(deps: Seq[ProjectReferenceEx]): CrossProject = {
        val refinedDeps: Seq[CrossClasspathDependency] = extractCrossDeps(deps)
        if (deps.isEmpty) { // CrossProject fails on empty list
          project
        } else {
          project.dependsOn(refinedDeps: _*)
        }
      }

      def depends(deps: ProjectReferenceEx*): CrossProject = {
        dependsSeq(deps)
      }
    }

  }


}
