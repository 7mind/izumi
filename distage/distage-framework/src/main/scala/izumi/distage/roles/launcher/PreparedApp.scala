package izumi.distage.roles.launcher

import izumi.distage.model.Locator
import izumi.distage.model.definition.Lifecycle
import izumi.functional.quasi.{QuasiIO, QuasiIORunner}

final case class PreparedApp[F[_]](
  appResource: Lifecycle[F, Locator],
  runner: QuasiIORunner[F],
  effect: QuasiIO[F],
)

object PreparedApp extends PreparedAppSyntax
