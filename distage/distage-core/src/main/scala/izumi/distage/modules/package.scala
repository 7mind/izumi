package izumi.distage

import izumi.distage.model.definition.Module

package object modules {
  /** Alias for `DefaultModule` parameterized at a 2-param BIO bifunctor. Identical to [[DefaultModule]] —
    * kept for source compatibility with the previous Session 2 surface.
    */
  type DefaultModule2[F[+_, +_]] = DefaultModule[F]
  object DefaultModule2 {
    @inline def apply[F[+_, +_]](module: Module): DefaultModule2[F] = DefaultModule(module)

    @inline def apply[F[+_, +_]](implicit modules: DefaultModule2[F], d: DummyImplicit): Module = modules.module
  }

  /** Alias for `DefaultModule` at a 3-param ZIO-shaped effect; partially applied at `Any` env. */
  type DefaultModule3[F[-_, +_, +_]] = DefaultModule[F[Any, +_, +_]]
  object DefaultModule3 {
    @inline def apply[F[-_, +_, +_]](module: Module): DefaultModule3[F] = DefaultModule(module)

    @inline def apply[F[-_, +_, +_]](implicit modules: DefaultModule3[F], d: DummyImplicit): Module = modules.module
  }
}
