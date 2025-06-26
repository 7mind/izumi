package izumi.distage.reflection.macros

import izumi.distage.model.reflection.SafeType
import izumi.reflect.Tag

import scala.quoted.{Expr, Quotes, Type}

object FunctoidMacroHelpers {
  final def generateSafeType[R: Type, Q <: Quotes](using qctx: Q)(ignoreDuringImplicitsSearch: List[qctx.reflect.Symbol] = Nil): Expr[SafeType] = {
    '{ 
      SafeType.get[R](
        using ${ 
          Expr.summonIgnoring[Tag[R]](using Type.of[Tag[R]])(using qctx)(ignoreDuringImplicitsSearch *)
            .getOrElse(qctx.reflect.report.errorAndAbort(s"Could not create Tag for ${Type.show[R]}"))
        }) 
    }
  }
}
