package sbt

import izumi.sbt.plugins.IzumiDslPlugin.autoImport.{In, WithBase}

import scala.reflect.macros.blackbox

object ExtendedProjectMacro {
  def projectUnifiedDslMacro(c: blackbox.Context): c.Expr[WithBase] = {
    import c.universe._
    val name: c.Expr[String] = extractName(c)
    reify {
      val enclosure = c.prefix.splice.asInstanceOf[In]
      val directory = enclosure.directory
      val settingGroups = enclosure.settingsGroups
      new WithBase(name.splice, directory, settingGroups)
    }
  }

  private def extractName(c: blackbox.Context): c.Expr[String] = {
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
