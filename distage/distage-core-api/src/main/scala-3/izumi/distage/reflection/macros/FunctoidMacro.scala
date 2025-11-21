package izumi.distage.reflection.macros

import izumi.distage.model.providers.Functoid
import izumi.distage.model.reflection.*
import izumi.distage.model.reflection.Provider.{ProviderImpl, ProviderType}

import scala.language.implicitConversions
import scala.quoted.{Expr, Quotes, Type}

object FunctoidMacro extends FunctoidMacroBase[Functoid] {
  transparent inline def make[R](inline fun: AnyRef): Functoid[R] = ${ makeImpl[R]('fun) }

  def makeImpl[R: Type](fun: Expr[AnyRef])(using qctx: Quotes): Expr[Functoid[R]] = {
    val idExtractor = new IdExtractorImpl[qctx.type]()
    val paramMacro = new FunctoidParametersMacro[qctx.type](idExtractor)
    val implicitsExtractorMacro = new DummyImplicitsExtractorMacro[qctx.type]()
    new FunctoidMacroImpl[qctx.type](paramMacro, implicitsExtractorMacro).make(fun)
  }

  protected def generateFunctoid[R: Type](
    using qctx: Quotes
  )(paramDefs: List[Expr[LinkedParameter]],
    originalFun: Expr[AnyRef],
    ignoreDuringImplicitsSearch: List[qctx.reflect.Symbol],
  ): Expr[Functoid[R]] = {
    '{
      val rawFn: AnyRef = ${ originalFun }
      new Functoid[R](
        new ProviderImpl[R](
          ${ Expr.ofList(paramDefs) },
          ${ FunctoidMacroHelpers.generateSafeType[R](using qctx)(ignoreDuringImplicitsSearch) },
          rawFn,
          (args: Seq[Any]) => ${ generateRawFnCall(paramDefs.size, 'rawFn, 'args) },
          ProviderType.Function,
        )
      )
    }
  }
}
