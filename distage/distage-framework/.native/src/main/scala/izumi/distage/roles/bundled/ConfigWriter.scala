package izumi.distage.roles.bundled

import izumi.distage.roles.model.{RoleDescriptor, RoleTask}
import izumi.functional.quasi.QuasiIO
import izumi.fundamentals.platform.cli.model.EntrypointArgs

import scala.annotation.unused

final class ConfigWriter[F[_]](F: QuasiIO[F]) extends RoleTask[F] with BundledTask {
  override def start(@unused roleParameters: EntrypointArgs): F[Unit] = {
    F.fail(new UnsupportedOperationException("ConfigWriter requires HOCON and derived configuration schemas, which are unavailable on Scala Native"))
  }
}

object ConfigWriter extends RoleDescriptor {
  override final val id = "configwriter"
}
