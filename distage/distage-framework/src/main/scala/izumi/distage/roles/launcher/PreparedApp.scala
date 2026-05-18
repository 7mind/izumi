package izumi.distage.roles.launcher

import izumi.distage.model.Locator
import izumi.distage.model.definition.Lifecycle
import izumi.functional.bio.{Async2, IO2, UnsafeRun2}

final case class PreparedApp[F[+_, +_]](
  appResource: Lifecycle[F, Throwable, Locator],
  roleAppEntrypoint: RoleAppEntrypoint[F],
  runner: UnsafeRun2[F],
  effect: IO2[F],
  effectAsync: Async2[F],
)

object PreparedApp extends PreparedAppSyntaxPlatformSpecific
