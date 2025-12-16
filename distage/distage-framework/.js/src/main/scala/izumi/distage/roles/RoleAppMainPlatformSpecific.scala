package izumi.distage.roles

import izumi.distage.roles.launcher.{AppFailureHandler, AppShutdownStrategy}
import izumi.fundamentals.platform.functional.Identity

import scala.concurrent.Future

private[roles] object RoleAppMainPlatformSpecific {
  type MainEffect[+A] = Future[A]

  def failedMain(t: Throwable): Future[Unit] = Future.failed(t)

  def defaultEarlyFailureHandler: AppFailureHandler = AppFailureHandler.NullHandler

  def defaultShutdownStrategy[F[_]]: AppShutdownStrategy.ImmediateExitShutdownStrategy[F] = new AppShutdownStrategy.ImmediateExitShutdownStrategy[F]

  def defaultIdentityShutdownStrategy: AppShutdownStrategy.ImmediateExitShutdownStrategy[Identity] = new AppShutdownStrategy.ImmediateExitShutdownStrategy[Identity]
}
