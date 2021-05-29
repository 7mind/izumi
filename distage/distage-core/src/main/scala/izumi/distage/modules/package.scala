package izumi.distage

import izumi.distage.model.definition.Module

package object modules {
  type DefaultModule2[F[_, _]] = DefaultModule[F[Throwable, _]]
  object DefaultModule2 {
    @inline def apply[F[_, _]](module: Module): DefaultModule2[F] = DefaultModule(module)

    @inline def apply[F[_, _]](implicit modules: DefaultModule2[F], d: DummyImplicit): Module = modules.module
  }

  type DefaultModule3[F[_, _, _]] = DefaultModule[F[Any, Throwable, _]]
  object DefaultModule3 {
    @inline def apply[F[_, _, _]](module: Module): DefaultModule3[F] = DefaultModule(module)

    @inline def apply[F[_, _, _]](implicit modules: DefaultModule3[F], d: DummyImplicit): Module = modules.module
  }
}
