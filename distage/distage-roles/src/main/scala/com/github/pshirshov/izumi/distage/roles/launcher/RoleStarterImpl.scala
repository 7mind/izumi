package com.github.pshirshov.izumi.distage.roles.launcher

import java.util.concurrent.{CountDownLatch, ExecutorService, TimeUnit}
import java.util.concurrent.atomic.AtomicReference

import com.github.pshirshov.izumi.distage.roles.launcher.exceptions.IntegrationCheckException
import com.github.pshirshov.izumi.distage.roles.roles._
import com.github.pshirshov.izumi.fundamentals.platform.integration.ResourceCheck
import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._
import com.github.pshirshov.izumi.logstage.api.IzLogger

import scala.util.Try
import scala.util.control.NonFatal


class RoleStarterImpl
(
  services: Set[RoleService],
  closeables: Set[AutoCloseable],
  executors: Set[ExecutorService],
  integrations: Set[IntegrationComponent],
  logger: IzLogger,
  lifecycleManager: ComponentsLifecycleManager
) extends RoleStarter {

  private val tasksCount = services.count(_.isInstanceOf[RoleTask])
  private val componentsCount = lifecycleManager.componentsNumber
  private val servicesCount = services.size
  private val integrationsCount = integrations.size

  private val state = new AtomicReference[StarterState](StarterState.NotYetStarted)
  private val latch = new CountDownLatch(1)

  private val shutdownHook = new Thread(() => {
    releaseThenStop()
  }, "role-hook")

  def start(): Unit = synchronized {
    if (state.compareAndSet(StarterState.NotYetStarted, StarterState.Starting)) {
      checkIntegrations()
      startComponents()
      state.set(StarterState.Started)
    } else {
      logger.info(s"App cannot be started at the moment, ${state.get() -> "state"}")
    }
  }

  def join(): Unit = synchronized {
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

  def stop(): Unit = synchronized {
    Runtime.getRuntime.removeShutdownHook(shutdownHook)
    releaseThenStop()
  }

  private def releaseThenStop(): Unit = synchronized {
    if (state.compareAndSet(StarterState.Started, StarterState.Stopping) || state.compareAndSet(StarterState.Starting, StarterState.Stopping)) {
      try {
        shutdownApp()
      } finally {
        latch.countDown()
        state.set(StarterState.Finished)
      }
    } else {
      logger.warn(s"Can't stop the app, unexpected ${state.get() -> "state"}")
    }
  }


  private def checkIntegrations(): Unit = {
    val checks = failingIntegrations()
    checks.fold(()) {
      failures =>
        throw new IntegrationCheckException(s"Integration check failed, failures were: ${failures.niceList()}", failures)
    }
  }

  private def startComponents(): Unit = {
    logger.info(s"Going to start ${(servicesCount - tasksCount) -> "daemons"}, ${tasksCount -> "tasks"}, ${componentsCount -> "components"}")
    lifecycleManager.startComponents()
  }

  private def failingIntegrations(): Option[Seq[ResourceCheck.Failure]] = {
    logger.info(s"Going to check availability of ${integrationsCount -> "resources"}")

    val failures = integrations.toSeq.flatMap {
      resource =>
        logger.debug(s"Checking $resource")
        try {
          resource.resourcesAvailable() match {
            case failure@ResourceCheck.ResourceUnavailable(description, Some(cause)) =>
              logger.debug(s"Integration check failed: $resource unavailable: $description, $cause")
              Some(failure)
            case failure@ResourceCheck.ResourceUnavailable(description, None) =>
              logger.debug(s"Integration check failed: $resource unavailable: $description")
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


  private def shutdownApp(): Unit = {
    logger.info("The app is going to shutdown...")
    val stopped = lifecycleManager.stopComponents()
    closeCloseables(stopped)
    shutdownExecutors()
  }

  private def shutdownExecutors(): Unit = {
    val toClose = executors
      .toList.reverse
      .filterNot(es => es.isShutdown || es.isTerminated)

    logger.info(s"Going to shutdown ${toClose.size -> "count" -> null} ${toClose.map(_.getClass).niceList() -> "executors"}")

    toClose.foreach {
      es =>
        logger.info(s"Going to close executor $es")
        es.shutdown()
        if (!es.awaitTermination(1, TimeUnit.SECONDS)) {
          val dropped = es.shutdownNow()
          logger.warn(s"Executor $es didn't finish in time, ${dropped.size()} tasks were dropped")
        }

    }
  }

  private def closeCloseables(ignore: Set[RoleComponent]): Unit = {
    val toClose = closeables
      .toList.reverse
      .filter {
        case rc: RoleComponent if ignore.contains(rc) =>
          false
        case _ => true
      }

    logger.info(s"Going to close ${toClose.size -> "count" -> null} ${toClose.map(_.getClass).niceList() -> "closeables"}")

    val (closed, failedToClose) = toClose
      .map(c => c -> Try(c.close()))
      .partition(_._2.isSuccess)
    logger.info(s"Service shutdown: ${closed.size -> "closed"} ; ${failedToClose.size -> "failed to close"}")
  }
}
