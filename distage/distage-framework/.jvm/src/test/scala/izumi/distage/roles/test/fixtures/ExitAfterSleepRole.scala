package izumi.distage.roles.test.fixtures

import izumi.distage.model.definition.Lifecycle
import izumi.functional.quasi.QuasiIO
import izumi.distage.roles.launcher.AppShutdownInitiator
import izumi.distage.roles.model.{RoleDescriptor, RoleService}
import izumi.fundamentals.platform.cli.model.raw.RawEntrypointParams
import izumi.logstage.api.IzLogger

class ExitAfterSleepRole[F[_]](logger: IzLogger, shutdown: AppShutdownInitiator)(implicit F: QuasiIO[F]) extends RoleService[F] {
  def runBadSleepingThread(id: String, cont: () => Unit): Unit = {
    def msg(s: String) = {
      println(s"$id: $s (direct message, will repeat in the logger)")
      logger.info(s"$id: $s (logged message)")
    }
    new Thread(new Runnable {
      override def run(): Unit = {
        val sleep = 5000L
        msg(s"sleeping ($sleep)...")
        Thread.sleep(sleep)
        msg(s"done sleeping ($sleep)")
        cont()
      }
    }).start()
  }

  override def start(roleParameters: RawEntrypointParams, freeArgs: Vector[String]): Lifecycle[F, Unit] = Lifecycle.make(
    F.maybeSuspend {
      logger.info(s"[ExitInTwoSecondsRole] started: $roleParameters, $freeArgs")
      runBadSleepingThread("init", () => shutdown.releaseAwaitLatch())
    }
  ) {
    _ =>
      F.maybeSuspend {
        logger.info(s"[ExitInTwoSecondsRole] exiting role...")
        runBadSleepingThread("release", () => ())
        logger.info(s"[ExitInTwoSecondsRole] still kicking!...")
      }
  }
}

object ExitAfterSleepRole extends RoleDescriptor {
  override final val id = "exitaftersleep"
}
