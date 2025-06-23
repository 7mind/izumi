package izumi.distage.model.definition.dsl

import izumi.distage.model.definition.dsl.ModuleDefDSL.MakeDSLBase
import izumi.distage.model.providers.Functoid
import izumi.distage.reflection.macros.Scala3FunctoidDummyImplicit
import izumi.reflect.Tag

trait ScalaVersionSpecificMakeDsl[T, AfterBind] {
  self: MakeDSLBase[T, AfterBind] =>
  def fromImplicit[I <: T: Tag, N <: Scala3FunctoidDummyImplicit](f: N ?=> Functoid[I]): AfterBind = {
    this.from[I](f(using null.asInstanceOf[N]))
  }
}


