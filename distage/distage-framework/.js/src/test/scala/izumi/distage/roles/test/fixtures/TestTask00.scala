package izumi.distage.roles.test.fixtures

import izumi.distage.roles.model.{RoleDescriptor, RoleTask}
import izumi.functional.quasi.QuasiIO
import izumi.fundamentals.platform.cli.model.EntrypointArgs
import izumi.logstage.api.IzLogger

class TestTask00[F[_]: QuasiIO](logger: IzLogger) extends RoleTask[F] {
  override def start(roleParameters: EntrypointArgs): F[Unit] = {
    QuasiIO[F].maybeSuspend {
      logger.info(s"[TestTask00] Entrypoint invoked!: $roleParameters")
    }
  }
}

object TestTask00 extends RoleDescriptor {
  override final val id = "testtask00"
}
