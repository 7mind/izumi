package izumi.fundamentals.platform.language.literals

import scala.language.dynamics
import scala.language.experimental.macros

/** To compile code with literal types using Scala 2.12, rewrite a literal like `mytype["abc"]` to `mytype[LiteralCompat.`"abc"`.T]` */
object LiteralCompat extends Dynamic {
  transparent inline def selectDynamic(inline literal: String): { type T } = ${ constantType('{ literal }) }

  def constantType(literal: quoted.Expr[String])(using quoted.Quotes): quoted.Expr[ { type T }] = {
    quoted.quotes.reflect.ConstantType(quoted.quotes.reflect.StringConstant(literal.valueOrAbort)).asType match {
      case '[l] => '{ ().asInstanceOf[ { type T = l }] }
    }
  }
}
