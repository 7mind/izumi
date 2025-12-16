package izumi.distage.roles.bundled

import izumi.distage.roles.model.{RoleDescriptor, RoleTask}
import izumi.functional.quasi.QuasiIO
import izumi.fundamentals.platform.cli.model.EntrypointArgs
import izumi.logstage.api.IzLogger

final class ConfigWriter[F[_]](
  logger: IzLogger,
  F: QuasiIO[F],
) extends RoleTask[F] {
  override def start(roleParameters: EntrypointArgs): F[Unit] = {
    F.maybeSuspend {
      logger.warn("ConfigWriter is not implemented on Scala.js")
    }
  }
}

object ConfigWriter extends RoleDescriptor {
  override final val id = "configwriter"
}
