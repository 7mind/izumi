package izumi.distage.reflection.macros

import izumi.distage.model.reflection.SafeType
import izumi.reflect.Tag

import scala.quoted.{Expr, Quotes, Type}

object FunctoidMacroHelpers {
  def generateSafeType[R: Type](using qctx: Quotes)(ignoreDuringImplicitsSearch: List[qctx.reflect.Symbol]): Expr[SafeType] = {
    val tagExpr = Expr
      .summonIgnoring[Tag[R]](ignoreDuringImplicitsSearch*)
      .getOrElse(qctx.reflect.report.errorAndAbort(s"Could not create Tag for ${Type.show[R]}"))

    '{ SafeType.get[R](using ${ tagExpr }) }
  }

}
