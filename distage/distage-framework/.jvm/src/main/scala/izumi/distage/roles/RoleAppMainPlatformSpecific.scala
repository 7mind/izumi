package izumi.distage.roles

import izumi.distage.roles.launcher.{AppFailureHandler, AppShutdownStrategy}
import izumi.fundamentals.platform.functional.Identity

import scala.annotation.unused

private[roles] object RoleAppMainPlatformSpecific {
  type MainEffect[+A] = A

  def runMain[F[_]](run: (Option[AppShutdownStrategy[F]] => Unit) => Unit): MainEffect[Unit] = run(_ => ())

  def failedMain(@unused t: Throwable): Unit = ()

  def defaultEarlyFailureHandler: AppFailureHandler = AppFailureHandler.TerminatingHandler

  def defaultShutdownStrategy[F[_]]: AppShutdownStrategy[F] = new AppShutdownStrategy.AsyncShutdownStrategy[F]

  def defaultIdentityShutdownStrategy: AppShutdownStrategy[Identity] = new AppShutdownStrategy.JvmExitHookBlockingShutdownStrategy[Identity]
}
