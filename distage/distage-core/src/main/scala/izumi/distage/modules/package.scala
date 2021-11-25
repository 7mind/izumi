package izumi.distage

import izumi.distage.model.definition.Module

package object modules {
  type DefaultModule2[F[_, _]] = DefaultModule[F[Throwable, _]]
  object DefaultModule2 {
    @inline def apply[F[_, _]](module: Module): DefaultModule2[F] = DefaultModule(module)

    // summoner
    @inline def apply[F[_, _]](implicit defaultModule: DefaultModule2[F], d: DummyImplicit): Module = defaultModule.module
  }

  type DefaultModule3[F[_, _, _]] = DefaultModule[F[Any, Throwable, _]]
  object DefaultModule3 {
    @inline def apply[F[_, _, _]](module: Module): DefaultModule3[F] = DefaultModule(module)

    // summoner
    @inline def apply[F[_, _, _]](implicit defaultModule: DefaultModule3[F], d: DummyImplicit): Module = defaultModule.module
  }
}
