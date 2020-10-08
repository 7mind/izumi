package izumi.distage.roles.launcher

import izumi.distage.model.Locator
import izumi.distage.model.definition.Lifecycle
import izumi.distage.model.effect.{QuasiIO, QuasiIORunner}

final case class PreparedApp[F[_]](
  appResource: Lifecycle[F, Locator],
  runner: QuasiIORunner[F],
  effect: QuasiIO[F],
) {
  def run(): Unit = {
    runner.run(appResource.use(_ => effect.unit)(effect))
  }
}
