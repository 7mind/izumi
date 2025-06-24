package izumi.distage.model.definition.dsl

import izumi.distage.model.definition.ImplDef
import izumi.distage.model.definition.dsl.ModuleDefDSL.MakeDSLBase
import izumi.distage.model.providers.Functoid
import izumi.distage.reflection.macros.Scala3FunctoidDummyImplicit
import izumi.reflect.Tag

trait ScalaVersionSpecificMakeDsl[T, AfterBind] {
  self: MakeDSLBase[T, AfterBind] =>
  def from[I <: T: Tag, N <: Scala3FunctoidDummyImplicit](f: N ?=> Functoid[I]): AfterBind = {
    val functoid: Functoid[I] = f(using null.asInstanceOf[N])
    bind(ImplDef.ProviderImpl(functoid.get.ret, functoid.get))
  }
}


