package izumi.distage.model.providers

import scala.language.implicitConversions

trait FunctoidMacroMethods {
  import FunctoidMacro.*

  inline implicit def apply[R](inline fun: () => R): Functoid[R] = make(fun)
  inline implicit def apply[R](inline fun: (?) => R): Functoid[R] = make(fun)
  inline implicit def apply[R](inline fun: (?, ?) => R): Functoid[R] = make(fun)
  inline implicit def apply[R](inline fun: (?, ?, ?) => R): Functoid[R] = make(fun)
  inline implicit def apply[R](inline fun: (?, ?, ?, ?) => R): Functoid[R] = make(fun)
  inline implicit def apply[R](inline fun: (?, ?, ?, ?, ?) => R): Functoid[R] = make(fun)
  inline implicit def apply[R](inline fun: (?, ?, ?, ?, ?, ?) => R): Functoid[R] = make(fun)
  inline implicit def apply[R](inline fun: (?, ?, ?, ?, ?, ?, ?) => R): Functoid[R] = make(fun)
  inline implicit def apply[R](inline fun: (?, ?, ?, ?, ?, ?, ?, ?) => R): Functoid[R] = make(fun)
  inline implicit def apply[R](inline fun: (?, ?, ?, ?, ?, ?, ?, ?, ?) => R): Functoid[R] = make(fun)
  inline implicit def apply[R](inline fun: (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) => R): Functoid[R] = make(fun)
  inline implicit def apply[R](inline fun: (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) => R): Functoid[R] = make(fun)
  inline implicit def apply[R](inline fun: (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) => R): Functoid[R] = make(fun)
  inline implicit def apply[R](inline fun: (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) => R): Functoid[R] = make(fun)
  inline implicit def apply[R](inline fun: (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) => R): Functoid[R] = make(fun)
  inline implicit def apply[R](inline fun: (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) => R): Functoid[R] = make(fun)
  inline implicit def apply[R](inline fun: (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) => R): Functoid[R] = make(fun)
  inline implicit def apply[R](inline fun: (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) => R): Functoid[R] = make(fun)
  inline implicit def apply[R](inline fun: (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) => R): Functoid[R] = make(fun)
  inline implicit def apply[R](inline fun: (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) => R): Functoid[R] = make(fun)
  inline implicit def apply[R](inline fun: (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) => R): Functoid[R] = make(fun)
  inline implicit def apply[R](inline fun: (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) => R): Functoid[R] = make(fun)
  inline implicit def apply[R](inline fun: (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) => R): Functoid[R] = make(fun)


}


object FunctoidMacro {
  import scala.quoted.{Expr, Quotes, Type}

  inline def make[R](inline fun: Any): Functoid[R] = ${ make('fun) }

  private def make[R](fun: Expr[Any])(using qctx: Quotes): Expr[Functoid[R]] = new CodePositionMaterializerMacro().make(fun)

  private final class CodePositionMaterializerMacro(using val qctx: Quotes) {

    import qctx.reflect._

    def make[R](fun: Expr[Any]): Expr[Functoid[R]] = {
      import qctx.reflect.*

      report.errorAndAbort(s"${System.nanoTime()}: ${fun.show}, ${fun.asTerm}")
      ???
    }
  }

//  def make[R](fun: Expr[Any])(using qctx: Quotes): Expr[Functoid[R]] = {
//    import qctx.reflect.*
//
//    report.errorAndAbort(s"${System.nanoTime()}: ${fun.show}, ${fun.asTerm}")
//    ???
//  }


}