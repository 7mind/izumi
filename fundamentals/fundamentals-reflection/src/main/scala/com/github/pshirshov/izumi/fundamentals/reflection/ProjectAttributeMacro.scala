package com.github.pshirshov.izumi.fundamentals.reflection

import scala.language.experimental.macros
import scala.reflect.macros.blackbox


object ProjectAttributeMacro {
  def extractSbtProjectGroupId(): Option[String] = macro extractProjectGroupIdMacro

  def extractSbtProjectVersion(): Option[String] = macro extractProjectVersionMacro

  def extract(name: String): Option[String] = macro extractAttrMacro

  def extractAttrMacro(c: blackbox.Context)(name:  c.Expr[String]): c.Expr[Option[String]] = {
    val nameStr = TreeTools.stringLiteral(c)(c.universe)(name.tree)
    extractAttr(c, nameStr)
  }

  def extractProjectGroupIdMacro(c: blackbox.Context)(): c.Expr[Option[String]] = {
    extractAttr(c, "product-group")
  }

  def extractProjectVersionMacro(c: blackbox.Context)(): c.Expr[Option[String]] = {
    extractAttr(c, "product-version")
  }

  private def extractAttr(c: blackbox.Context, name: String): c.Expr[Option[String]] = {
    val prefix = s"$name-"
    val value = c.settings.find(_.startsWith(prefix)).map(_.stripPrefix(prefix))
    if (value.isEmpty) {
      c.warning(c.enclosingPosition, s"Undefined macro parameter $name, add `-Xmacro-settings:$name=<value>` into `scalac` options")
    }

    import c.universe._
    c.Expr[Option[String]](q"$value")
  }

}
