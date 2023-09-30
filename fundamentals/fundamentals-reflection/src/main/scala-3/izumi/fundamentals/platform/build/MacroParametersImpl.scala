package izumi.fundamentals.platform.build

import izumi.fundamentals.reflection.ReflectiveCall

import scala.annotation.experimental
import scala.quoted.{Expr, Quotes, Type}

@experimental
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
    val prefix = s"$name="
    val value = CompilationInfo.XmacroSettings.filter(_.startsWith(prefix)).map(_.stripPrefix(prefix)).lastOption
    if (value.isEmpty) {
      report.info(s"Undefined macro parameter $name, add `-Xmacro-settings:$prefix<value>` into `scalac` options")
    }
    value
  }
}
