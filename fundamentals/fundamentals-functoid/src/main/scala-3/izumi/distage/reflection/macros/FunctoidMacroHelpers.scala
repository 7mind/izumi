package izumi.distage.reflection.macros

import izumi.distage.model.reflection.SafeType
import izumi.reflect.Tag

import scala.quoted.{Expr, Quotes, Type}

object FunctoidMacroHelpers {
  final def generateSafeType[R: Type, Q <: Quotes](using qctx: Q): Expr[SafeType] = {
    '{ SafeType.get[R](using scala.compiletime.summonInline[Tag[R]]) }
  }
}
