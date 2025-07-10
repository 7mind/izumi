package izumi.distage.reflection.macros

import izumi.distage.model.reflection.SafeType
import izumi.reflect.Tag

import scala.quoted.{Expr, Quotes, Type}

object FunctoidMacroHelpers {
  final def generateSafeType[R: Type](using qctx: Quotes)(ignoreDuringImplicitsSearch: List[qctx.reflect.Symbol] = Nil): Expr[SafeType] = {
    val tagExpr = Expr
      .summonIgnoring[Tag[R]](ignoreDuringImplicitsSearch*)
      .getOrElse(qctx.reflect.report.errorAndAbort(s"Could not create Tag for ${Type.show[R]}"))

    val safeType = '{ SafeType.get[R](using ${ tagExpr }) }
    println(s"generateSafeType found for Tag[${Type.show[R]}] ignores:$ignoreDuringImplicitsSearch => ${safeType.show}")
    safeType
  }
}
