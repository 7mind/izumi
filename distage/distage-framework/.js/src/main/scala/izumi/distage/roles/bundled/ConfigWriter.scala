package izumi.distage.roles.bundled

import izumi.distage.roles.model.{RoleDescriptor, RoleTask}
import izumi.functional.bio.IO2
import izumi.fundamentals.platform.cli.model.EntrypointArgs
import izumi.logstage.api.IzLogger

final class ConfigWriter[F[+_, +_]](
  logger: IzLogger,
  F: IO2[F],
) extends RoleTask[F] {
  override def start(roleParameters: EntrypointArgs): F[Throwable, Unit] = {
    F.syncThrowable {
      logger.warn("ConfigWriter is not implemented on Scala.js")
    }
  }
}

object ConfigWriter extends RoleDescriptor {
  override final val id = "configwriter"
}
