package izumi.distage.roles.launcher

import izumi.distage.model.Locator
import izumi.distage.model.definition.Lifecycle
import izumi.functional.bio.{Async1, IO1, IORunner1}

final case class PreparedApp[F[_]](
  appResource: Lifecycle[F, Locator],
  roleAppEntrypoint: RoleAppEntrypoint[F],
  runner: IORunner1[F],
  effect: IO1[F],
  effectAsync: Async1[F],
)

object PreparedApp extends PreparedAppSyntaxPlatformSpecific
