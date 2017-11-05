package sbt

import org.bitbucket.pshirshov.izumi.sbt.definitions.IzumiDsl.WithBase

import scala.reflect.macros._

object ExtendedProjectMacro {
  def projectUnifiedDslMacro(c: blackbox.Context): c.Expr[WithBase] = {
    import c.universe._
    val name: c.Expr[String] = extractName(c)
    reify {
      import org.bitbucket.pshirshov.izumi.sbt.definitions.IzumiDsl._
      val directory = c.prefix.splice.asInstanceOf[In].directory
      new WithBase(name.splice, directory)
    }
  }

  private def extractName(c: blackbox.Context) = {
    import c.universe._
    val enclosingValName = std.KeyMacro.definingValName(
      c,
      methodName =>
        s"""$methodName must be directly assigned to a val, such as `val x = $methodName`. Alternatively, you can use `sbt.Project.apply`"""
    )
    val normalized = enclosingValName.map {
      case chr if chr.isUpper =>
        s"-${chr.toLower}"
      case chr =>
        s"$chr"
    }
    val name = c.Expr[String](Literal(Constant(normalized.mkString)))
    name
  }

}
