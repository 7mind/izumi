package com.github.pshirshov.izumi.distage.roles.launcher

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference

import com.github.pshirshov.izumi.distage.roles.launcher.exceptions.IntegrationCheckException
import com.github.pshirshov.izumi.distage.roles.roles._
import com.github.pshirshov.izumi.fundamentals.platform.integration.ResourceCheck
import com.github.pshirshov.izumi.logstage.api.IzLogger

import scala.util.Try
import scala.util.control.NonFatal


class RoleStarterImpl(
                       services: Set[RoleService]
                       , components: Set[RoleComponent]
                       , closeables: Set[AutoCloseable]
                       , integrations: Set[IntegrationComponent]
                       , logger: IzLogger
                     ) extends RoleStarter {

  private val tasksCount = services.count(_.isInstanceOf[RoleTask])
  private val componentsCount = components.size
  private val servicesCount = services.size
  private val integrationsCount = integrations.size

  private val state = new AtomicReference[StarterState](StarterState.NotYetStarted)
  private val latch = new CountDownLatch(1)

  private val shutdownHook = new Thread(() => {
    releaseThenStop()
  }, "role-hook")

  def start(): Unit = {
    if (state.compareAndSet(StarterState.NotYetStarted, StarterState.Starting)) {
      val checks = checkIntegrations()
      checks.fold(()) {
        failures =>
          throw new IntegrationCheckException(s"Integration check failed, failures were: $failures", failures)
      }
      logger.info(s"Going to start ${(servicesCount - tasksCount) -> "daemons"}, ${tasksCount -> "tasks"}, ${componentsCount -> "components"}")
      components.foreach {
        service =>
          logger.info(s"Starting component $service...")
          service.start()
      }
      state.set(StarterState.Started)
    } else {
      logger.info(s"App cannot be started at the moment, ${state.get() -> "state"}")
    }
  }

  def join(): Unit = {
    if (services.size > tasksCount) {
      logger.info("Startup finished, joining on main thread...")
      Runtime.getRuntime.addShutdownHook(shutdownHook)
      latch.await()
    } else {
      if (services.isEmpty) {
        logger.info("No roles to activate, exiting...")
      } else {
        logger.info("No daemonic roles, exiting...")
      }
      releaseThenStop()
    }
  }

  def stop(): Unit = {
    Runtime.getRuntime.removeShutdownHook(shutdownHook)
    releaseThenStop()
  }

  private def checkIntegrations(): Option[Seq[ResourceCheck.Failure]] = {
    logger.info(s"Going to check availability of ${integrationsCount -> "resources"}")

    val failures = integrations.toSeq.flatMap {
      resource =>
        logger.debug(s"Checking $resource")
        try {
          resource.resourcesAvailable() match {
            case failure@ResourceCheck.ResourceUnavailable(description, cause) =>
              logger.error(s"Integration check failed: Resource unavailable $description, $resource, $cause")
              Some(failure)
            case ResourceCheck.Success() =>
              None
          }
        } catch {
          case NonFatal(exception) =>
            logger.error(s"Integration check for $resource threw $exception")
            Some(ResourceCheck.ResourceUnavailable(exception.getMessage, Some(exception)))
        }
    }
    Some(failures).filter(_.nonEmpty)
  }

  private def releaseThenStop(): Unit = {
    if (state.compareAndSet(StarterState.Started, StarterState.Stopping) || state.compareAndSet(StarterState.Starting, StarterState.Stopping)) {
      try {
        finalizeApp()
      } finally {
        latch.countDown()
        state.set(StarterState.Finished)
      }
    } else {
      logger.warn(s"Can't stop the app, unexpected ${state.get() -> "state"}")
    }
  }

  private def finalizeApp(): Unit = {
    import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._

    logger.info("The app is going to shutdown...")
    logger.info(s"Going to stop ${components.size -> "count" -> null} ${components.map(_.getClass).niceList() -> "components"}")

    val (stopped, failed) = components
      .toList.reverse
      .map(s => s -> Try(s.stop()))
      .partition(_._2.isSuccess)
    logger.info(s"Service shutdown: ${stopped.size -> "stopped"} ; ${failed.size -> "failed to stop"}")

    val toClose = closeables
      .toList.reverse
      .filterNot(_.isInstanceOf[RoleComponent])
    logger.info(s"Going to close ${toClose.size -> "count" -> null} ${toClose.map(_.getClass).niceList() -> "closeables"}")

    val (closed, failedToClose) = toClose
      .map(c => c -> Try(c.close()))
      .partition(_._2.isSuccess)
    logger.info(s"Service shutdown: ${closed.size -> "closed"} ; ${failedToClose.size -> "failed to close"}")
  }
}
