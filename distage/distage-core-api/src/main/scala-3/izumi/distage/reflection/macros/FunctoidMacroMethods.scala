package izumi.distage.reflection.macros

import izumi.distage.model.providers.Functoid
import izumi.distage.model.reflection.*
import izumi.distage.model.reflection.Provider.{ProviderImpl, ProviderType}

import scala.language.implicitConversions
import scala.quoted.{Expr, Quotes, Type}

trait FunctoidMacroMethods extends FunctoidMacroMethodsBase {}

object FunctoidMacro extends FunctoidMacroBase[Functoid] {
  transparent inline def make[R](inline fun: AnyRef): Functoid[R] = ${ makeImpl[R]('fun) }

  def makeImpl[R: Type](fun: Expr[AnyRef])(using qctx: Quotes): Expr[Functoid[R]] = new FunctoidMacroImpl[qctx.type]().make(fun)

  protected def generateFunctoid[R: Type, Q <: Quotes](paramDefs: List[Expr[LinkedParameter]], originalFun: Expr[AnyRef])(using qctx: Q): Expr[Functoid[R]] = {
    '{
      val rawFn: AnyRef = ${ originalFun }
      new Functoid[R](
        new ProviderImpl[R](
          ${ Expr.ofList(paramDefs) },
          ${ generateSafeType[R, Q] },
          rawFn,
          (args: Seq[Any]) => ${ generateRawFnCall(paramDefs.size, 'rawFn, 'args) },
          ProviderType.Function,
        )
      )
    }
  }
}
