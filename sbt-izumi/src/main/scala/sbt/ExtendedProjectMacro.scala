package sbt

import scala.reflect.macros._

object ExtendedProjectMacro {
  def projectExFullMacroImpl(c: blackbox.Context)(directory: c.Expr[String]): c.Expr[Project] = {
    import c.universe._
    val name: c.Expr[String] = extractName(c)
    reify {
      import org.bitbucket.pshirshov.izumi.sbt.definitions.ExtendedProjectsGlobalDefs._
      Project(name.splice, new File(s"${directory.splice}/${name.splice}"))
        .extend
        .registered
    }
  }

  def projectExConfiguredMacroImpl(c: blackbox.Context)(directory: c.Expr[String]): c.Expr[Project] = {
    import c.universe._
    val name: c.Expr[String] = extractName(c)
    reify {
      import org.bitbucket.pshirshov.izumi.sbt.definitions.ExtendedProjectsGlobalDefs._
      Project(name.splice, new File(s"${directory.splice}/${name.splice}"))
        .globalSettings
    }
  }

  def projectExRootMacroImpl(c: blackbox.Context)(directory: c.Expr[String]): c.Expr[Project] = {
    import c.universe._
    val name: c.Expr[String] = extractName(c)
    reify {
      import org.bitbucket.pshirshov.izumi.sbt.definitions.ExtendedProjectsGlobalDefs._
      Project(name.splice, new File(s"${directory.splice}"))
        .defaultRoot
    }
  }

  private def extractName(c: blackbox.Context) = {
    import c.universe._
    val enclosingValName = std.KeyMacro.definingValName(
      c,
      methodName =>
        s"""$methodName must be directly assigned to a val, such as `val x = $methodName`. Alternatively, you can use `sbt.Project.apply`""")
    val name = c.Expr[String](Literal(Constant(enclosingValName)))
    name
  }

}
