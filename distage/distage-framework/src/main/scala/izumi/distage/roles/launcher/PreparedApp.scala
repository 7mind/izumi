package izumi.distage.roles.launcher

import izumi.distage.model.Locator
import izumi.distage.model.definition.Lifecycle
import izumi.functional.quasi.{QuasiAsync, QuasiIO, QuasiIORunner}

final case class PreparedApp[F[_]](
  appResource: Lifecycle[F, Locator],
  roleAppEntrypoint: RoleAppEntrypoint[F],
  runner: QuasiIORunner[F],
  effect: QuasiIO[F],
  effectAsync: QuasiAsync[F],
)

object PreparedApp extends PreparedAppSyntaxPlatformSpecific
