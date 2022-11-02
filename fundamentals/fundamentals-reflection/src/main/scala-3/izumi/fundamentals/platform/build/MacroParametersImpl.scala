package izumi.fundamentals.platform.build

import izumi.fundamentals.reflection.ReflectiveCall

object MacroParametersImpl {
  import scala.quoted.{Expr, Quotes, Type}

  def extractString(key: Expr[String])(using quotes: Quotes): Expr[Option[String]] = {
    '{ ${ extract(key) }.lastOption }
  }

  def extractBool(key: Expr[String])(using quotes: Quotes): Expr[Option[Boolean]] = {
    '{ ${ extract(key) }.lastOption.map(_.toLowerCase).map(v => v == "true" || v == "1") }
  }

  private def extract(key: Expr[String])(using quotes: Quotes): Expr[List[String]] = {
    import quotes.reflect.*
    val k = key.valueOrAbort
    val prefix = s"$k="

    // CompilationInfo.XmacroSettings
    val out = ReflectiveCall.call[List[String]](CompilationInfo, "XmacroSettings")

    val values = out.filter(_.startsWith(prefix)).map(_.stripPrefix(prefix))

    Expr(values)
  }
}
