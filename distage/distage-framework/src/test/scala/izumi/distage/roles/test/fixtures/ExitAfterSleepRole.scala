package izumi.distage.roles.test.fixtures

import izumi.distage.model.definition.Lifecycle
import izumi.distage.roles.launcher.AppShutdownInitiator
import izumi.distage.roles.model.{RoleDescriptor, RoleService}
import izumi.functional.bio.IO2
import izumi.fundamentals.platform.cli.model.EntrypointArgs
import izumi.logstage.api.IzLogger

class ExitAfterSleepRole[F[+_, +_]](logger: IzLogger, shutdown: AppShutdownInitiator)(implicit F: IO2[F]) extends RoleService[F] {
  def runBadSleepingThread(id: String, cont: () => Unit): Unit = {
    def msg(s: String): Unit = {
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

  override def start(roleParameters: EntrypointArgs): Lifecycle[F, Throwable, Unit] = Lifecycle.make(
    F.syncThrowable {
      logger.info(s"[ExitInTwoSecondsRole] started: $roleParameters")
      runBadSleepingThread("init", () => shutdown.releaseAwaitLatch())
    }
  ) {
    _ =>
      F.sync {
        logger.info(s"[ExitInTwoSecondsRole] exiting role...")
        runBadSleepingThread("release", () => ())
        logger.info(s"[ExitInTwoSecondsRole] still kicking!...")
      }
  }
}

object ExitAfterSleepRole extends RoleDescriptor {
  override final val id = "exitaftersleep"
}
