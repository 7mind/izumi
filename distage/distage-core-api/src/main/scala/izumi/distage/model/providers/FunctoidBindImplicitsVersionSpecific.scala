package izumi.distage.model.providers

import izumi.distage.reflection.macros.{DischargeDummyMacro, IndiscriminateFunctoidDummyImplicit, UnresolvedOnlyFunctoidDummyImplicit}

trait FunctoidBindImplicitsVersionSpecific {

  /**
    * Convert __unresolved__ implicits in the following code block, lambda or a method reference
    * into explicit parameters that will be resolved from the object graph, not the implicit context.
    *
    * Example:
    *
    * {{{
    *   final case class X(a: Int, b: Double)
    *
    *   // LAMBDA:
    *
    *   make[X].from(bindImplicits {
    *     (i: Int) => X(i, implicitly[Double])
    *   }
    *
    *   // transforms into:
    *
    *   make[X].from {
    *     (i: Int, d: Double) => X(i, d)
    *   }
    *
    *   // CODE BLOCK:
    *
    *   make[X].from(bindImplicits {
    *     X(implicitly[Int], implicitly[Double])
    *   })
    *
    *   // transforms into:
    *
    *   make[X].from {
    *     (i: Int, d: Double) => X(i, d)
    *   }
    *
    *   // METHOD REFERENCE:
    *
    *   def createX(i: Int)(implicit d: Double, dx: DummyImplicit): X = X(i, d)
    *
    *   make[X].from(bindImplicits {
    *     createX
    *   })
    *
    *   // transforms into:
    *
    *   make[X].from {
    *     (i: Int, d: Double) => createX(i)(using d, DummyImplicit.dummyImplicit)
    *   }
    *
    *   // because search for DummyImplicit was __resolved__ in current implicit scope, it was taken from the implicit context,
    *   // not from the object graph!
    * }}}
    *
    * @note This function is available inside ModuleDef DSL, but it can also be called outside of it as [[Functoid.bindImplicits]]
    */
  inline final def bindImplicits[I, N <: UnresolvedOnlyFunctoidDummyImplicit](inline f: N ?=> Functoid[I]): Functoid[I] = {
    DischargeDummyMacro.dischargeDummy[I, N](f)
  }

  /**
    * Like [[bindImplicits]], but converts even __fully resolved__ implicits into Functoid parameters.
    *
    * This may not be what you want, because this will also capture global utility implicits such
    * as `DummyImplicit`, `ClassTag` and `scala.collection.Factory`, and turn them into DI parameters.
    */
  inline final def bindAllImplicits[I, N <: IndiscriminateFunctoidDummyImplicit](inline f: N ?=> Functoid[I]): Functoid[I] = {
    DischargeDummyMacro.dischargeDummy[I, N](f)
  }

}
