package izumi.distage.model.definition.dsl

import izumi.distage.model.providers.Functoid
import izumi.distage.reflection.macros.{DischargeDummyMacro, IgnorableFunctoidDummyImplicit, UnignorableDummyImplicit}

trait ScalaVersionSpecificBindDSL {
  inline final def bindImplicits[I, N <: IgnorableFunctoidDummyImplicit](inline f: N ?=> Functoid[I]): Functoid[I] = {
    DischargeDummyMacro.dischargeDummy[I, N](f)
  }

  inline final def bindAllImplicits[I, N <: UnignorableDummyImplicit](inline f: N ?=> Functoid[I]): Functoid[I] = {
    DischargeDummyMacro.dischargeDummy[I, N](f)
  }
}
