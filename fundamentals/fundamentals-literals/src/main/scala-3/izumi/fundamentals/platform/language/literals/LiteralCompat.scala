package izumi.fundamentals.platform.language.literals

import scala.language.dynamics
import scala.language.experimental.macros
import scala.quoted.{Expr, Quotes}

/** To compile code with literal types using Scala 2.12, rewrite a literal like `mytype["abc"]` to `mytype[LiteralCompat.`"abc"`.T]` */
object LiteralCompat extends Dynamic {
  transparent inline def selectDynamic(inline literal: String): { type T } = ${ constantType('{ literal }) }

  def constantType(literal: Expr[String])(using quotes: Quotes): Expr[{ type T }] = {
    import quotes.reflect.*
    val literalStr = literal.valueOrAbort
    val parsedConstant: Constant = if (literalStr.startsWith("\"\"\"")) {
      StringConstant(literalStr.stripPrefix("\"\"\"").stripSuffix("\"\"\""))
    } else if (literalStr.startsWith("\"")) {
      StringConstant(literalStr.stripPrefix("\"").stripSuffix("\""))
    } else if (literalStr.startsWith("\'")) {
      CharConstant(literalStr.charAt(1))
    } else if (literalStr == "()") {
      UnitConstant()
    } else if (literalStr == "true") {
      BooleanConstant(true)
    } else if (literalStr == "false") {
      BooleanConstant(false)
    } else if (literalStr == "null") {
      NullConstant()
    } else {
      literalStr.toIntOption
        .map(IntConstant(_))
        .orElse(literalStr.toDoubleOption.map(DoubleConstant(_)))
        .getOrElse(report.errorAndAbort(s"Couldn't parse `$literalStr` as a Scala literal`"))
    }
    quotes.reflect.ConstantType(parsedConstant).asType match {
      case '[l] => '{ ().asInstanceOf[{ type T = l }] }
    }
  }
}
