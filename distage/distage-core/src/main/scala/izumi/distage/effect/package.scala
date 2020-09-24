package izumi.distage

import izumi.distage.model.definition.Module

package object effect {
  type DefaultModules2[F[_, _]] = DefaultModules[F[Throwable, ?]]
  object DefaultModules2 {
    @inline def apply[F[_, _]: DefaultModules2](implicit d: DummyImplicit): DefaultModules2[F] = implicitly
    @inline def apply[F[_, _]](modules: Seq[Module]): DefaultModules2[F] = DefaultModules(modules)
  }

  type DefaultModules3[F[_, _, _]] = DefaultModules[F[Any, Throwable, ?]]
  object DefaultModules3 {
    @inline def apply[F[_, _, _]: DefaultModules3](implicit d: DummyImplicit): DefaultModules3[F] = implicitly
    @inline def apply[F[_, _, _]](modules: Seq[Module]): DefaultModules3[F] = DefaultModules(modules)
  }
}
