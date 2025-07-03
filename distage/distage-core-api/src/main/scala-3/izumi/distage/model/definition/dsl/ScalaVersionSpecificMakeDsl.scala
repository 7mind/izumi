package izumi.distage.model.definition.dsl

import izumi.distage.model.definition.ImplDef
import izumi.distage.model.definition.dsl.ModuleDefDSL.MakeDSLBase
import izumi.distage.model.providers.Functoid
import izumi.distage.reflection.macros.{DischargeDummyMacro, IgnorableFunctoidDummyImplicit, UnignorableDummyImplicit}
import izumi.reflect.Tag

trait ScalaVersionSpecificMakeDsl[T, AfterBind] { self: MakeDSLBase[T, AfterBind] =>
  inline def from[I <: T: Tag, N <: IgnorableFunctoidDummyImplicit](inline f: N ?=> Functoid[I]): AfterBind = {
    val functoid: Functoid[I] = DischargeDummyMacro.dischargeDummy[I, N](f)
    bind(ImplDef.ProviderImpl(functoid.get.ret, functoid.get))
  }

  inline def noCapture[I <: T: Tag](inline f: Functoid[I]): AfterBind = {
    from(_ ?=> f)
  }
}

object ScalaVersionSpecificMakeDsl {
  inline def withAllImplicits[I: Tag, N <: UnignorableDummyImplicit](f: N ?=> I): I = f(using null.asInstanceOf[N])
}
