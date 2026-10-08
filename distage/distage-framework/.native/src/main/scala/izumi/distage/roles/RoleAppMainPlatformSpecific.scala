package izumi.distage.roles

import izumi.distage.roles.launcher.{AppFailureHandler, AppShutdownStrategy, NativeShutdownStrategy}
import izumi.distage.roles.launcher.NativeShutdownStrategy.LauncherShutdownCompletion
import izumi.fundamentals.platform.functional.Identity

import scala.annotation.unused

private[roles] object RoleAppMainPlatformSpecific {
  type MainEffect[+A] = A

  def runMain[F[_]](run: (Option[AppShutdownStrategy[F]] => Unit) => Unit): MainEffect[Unit] = {
    var observed = false
    var completion = Option.empty[LauncherShutdownCompletion]
    try {
      run {
        strategy =>
          require(!observed, "Launcher shutdown strategy was observed more than once")
          observed = true
          strategy.foreach {
            case native: LauncherShutdownCompletion =>
              native.deferShutdownCompletion()
              completion = Some(native)
            case _ =>
          }
      }
    } finally completion.foreach(_.completeShutdown())
  }

  def failedMain(@unused t: Throwable): Unit = ()

  def defaultEarlyFailureHandler: AppFailureHandler = AppFailureHandler.TerminatingHandler

  def defaultShutdownStrategy[F[_]]: AppShutdownStrategy[F] = new NativeShutdownStrategy[F]

  def defaultIdentityShutdownStrategy: AppShutdownStrategy[Identity] = new NativeShutdownStrategy[Identity]
}
