package izumi.distage

import izumi.distage.model.definition.{Activation, BootstrapContextModule, BootstrapModule}
import izumi.functional.quasi.QuasiIO
import izumi.distage.model.recursive.Bootloader
import izumi.distage.model.reflection.DIKey
import izumi.distage.model.{Injector, Locator, PlannerInput}
import izumi.distage.modules.DefaultModule
import izumi.fundamentals.platform.functional.Identity
import izumi.reflect.TagK

private[distage] trait InjectorFactoryDottyWorkarounds { this: InjectorFactory =>

  // FIXME: workaround for https://github.com/lampepfl/dotty/issues/15913, remove when fixed
  @deprecated("FIXME: workaround for https://github.com/lampepfl/dotty/issues/15913, remove when fixed")
  final def apply[F[_]: QuasiIO: TagK: DefaultModule](
    bootstrapActivation: Activation,
    parent: Option[Locator],
  ): Injector[F] = {
    apply[F](defaultBootstrap, defaultBootstrapActivation ++ bootstrapActivation, parent, Nil)
  }

  // FIXME: workaround for https://github.com/lampepfl/dotty/issues/15913, remove when fixed
  @deprecated("FIXME: workaround for https://github.com/lampepfl/dotty/issues/15913, remove when fixed")
  final def apply[F[_]: QuasiIO: TagK: DefaultModule](
    bootstrapActivation: Activation,
    parent: Option[Locator],
    overrides: Seq[BootstrapModule],
  ): Injector[F] = {
    apply[F](defaultBootstrap, defaultBootstrapActivation ++ bootstrapActivation, parent, overrides)
  }

  // FIXME: workaround for https://github.com/lampepfl/dotty/issues/15913, remove when fixed
  @deprecated("FIXME: workaround for https://github.com/lampepfl/dotty/issues/15913, remove when fixed")
  final def apply[F[_]: QuasiIO: TagK: DefaultModule](
    bootstrapActivation: Activation,
    overrides: Seq[BootstrapModule],
  ): Injector[F] = {
    apply[F](defaultBootstrap, defaultBootstrapActivation ++ bootstrapActivation, None, overrides)
  }

}
