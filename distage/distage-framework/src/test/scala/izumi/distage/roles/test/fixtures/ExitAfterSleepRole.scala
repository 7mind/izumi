package izumi.distage.roles.test.fixtures

import izumi.distage.model.definition.Lifecycle
import izumi.distage.model.effect.QuasiIO
import izumi.distage.roles.launcher.{AppShutdownInitiator, AppShutdownStrategy}
import izumi.distage.roles.model.{RoleDescriptor, RoleService}
import izumi.fundamentals.platform.cli.model.raw.RawEntrypointParams
import izumi.logstage.api.IzLogger

class ExitAfterSleepRole[F[_] : QuasiIO](logger: IzLogger, shutdown: AppShutdownInitiator[F]) extends RoleService[F] {
  def runBadSleepingThread(id: String, cont: () => Unit): Unit = {
    new Thread(new Runnable {
      override def run(): Unit = {
        logger.info(s"$id: sleeping...")
        Thread.sleep(5000)
        logger.info(s"$id: done sleeping!...")
        cont()
      }
    }).start()
  }

  override def start(roleParameters: RawEntrypointParams, freeArgs: Vector[String]): Lifecycle[F, Unit] = Lifecycle.make(
    QuasiIO[F].maybeSuspend {
      logger.info(s"[ExitInTwoSecondsRole] started: $roleParameters, $freeArgs")
      runBadSleepingThread("init", shutdown.releaseAwaitLatch)
    }
  ) {
    _ =>
      QuasiIO[F].maybeSuspend {
        logger.info(s"[ExitInTwoSecondsRole] exiting role...")
        runBadSleepingThread("release", () => ())
        logger.info(s"[ExitInTwoSecondsRole] still kicking!...")
      }
  }
}

object ExitAfterSleepRole extends RoleDescriptor {
  override final val id = "exitaftersleep"
}
