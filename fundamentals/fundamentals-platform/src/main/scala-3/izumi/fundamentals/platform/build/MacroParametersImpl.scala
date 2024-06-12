package izumi.fundamentals.platform.build

import scala.quoted.{Expr, Quotes, Type}

object MacroParametersImpl {

  def extractString(name: Expr[String])(using quotes: Quotes): Expr[Option[String]] = {
    Expr(extract(name.valueOrAbort))
  }

  def extractBool(name: Expr[String])(using quotes: Quotes): Expr[Option[Boolean]] = {
    val value = extract(name.valueOrAbort)
    val isTrue = value.map(_.toLowerCase).map(v => v == "true" || v == "1")
    Expr(isTrue)
  }

  private def extract(name: String)(using quotes: Quotes): Option[String] = {
    import quotes.reflect.*
    import scala.reflect.Selectable.reflectiveSelectable
    val prefix = s"$name="
    val value = CompilationInfo.asInstanceOf[{ def XmacroSettings: List[String] }].XmacroSettings.filter(_.startsWith(prefix)).map(_.stripPrefix(prefix)).lastOption
    if (value.isEmpty) {
      report.info(s"Undefined macro parameter $name, add `-Xmacro-settings:$prefix<value>` into `scalac` options")
    }
    value
  }
}
