package izumi.distage.roles

import izumi.distage.roles.launcher.{AppFailureHandler, AppShutdownStrategy}
import izumi.functional.bio.Bifunctorized

import scala.annotation.unused

private[roles] object RoleAppMainPlatformSpecific {
  type MainEffect[+A] = A

  def failedMain(@unused t: Throwable): Unit = ()

  def defaultEarlyFailureHandler: AppFailureHandler = AppFailureHandler.TerminatingHandler

  def defaultShutdownStrategy[F[+_, +_]]: AppShutdownStrategy[F] = new AppShutdownStrategy.AsyncShutdownStrategy[F]

  def defaultIdentityShutdownStrategy: AppShutdownStrategy[Bifunctorized.IdentityBifunctorized] = new AppShutdownStrategy.JvmExitHookBlockingShutdownStrategy[Bifunctorized.IdentityBifunctorized]
}
