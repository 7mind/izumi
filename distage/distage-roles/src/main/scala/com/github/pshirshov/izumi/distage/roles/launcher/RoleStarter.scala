package com.github.pshirshov.izumi.distage.roles.launcher

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference

import com.github.pshirshov.izumi.distage.model.Locator
import com.github.pshirshov.izumi.distage.roles.roles.{RoleAppComponent, RoleAppService, RoleAppTask}
import com.github.pshirshov.izumi.logstage.api.IzLogger

import scala.util.Try

// TODO: we need to rewrite this once we have WeakRefs
class RoleStarter(services: Set[RoleAppService], components: Set[RoleAppComponent], closeables: Set[AutoCloseable], logger: IzLogger) {
  private val latch = new CountDownLatch(1)

  private val contextRef = new AtomicReference[Locator]()

  def start(context: Locator): Unit = {
    if (contextRef.compareAndSet(null, context)) {
      val tasksCount = services.count(_.isInstanceOf[RoleAppTask])
      logger.info(s"${(services.size - tasksCount) -> "services"}; ${tasksCount -> "tasks"}; ${components.size -> "components"} are going to start...")

      components.foreach {
        service =>
          logger.info(s"Starting component $service...")
          service.start()
      }

      if (services.isEmpty) {
        logger.info("No roles to activate, exiting...")
      } else if (services.size > tasksCount) {
        logger.info("Startup finished, joining on main thread...")
        setupShutdownHook()
        latch.await()
      } else {
        logger.info("No daemonic roles, exiting...")
      }
    } else {
      logger.info("App is already started")
    }
  }

  private def setupShutdownHook(): Unit = {
    Runtime.getRuntime.addShutdownHook(new Thread(() => {
      latch.countDown()
      doStop()
    }))
  }

  def stop(): Unit = {
    latch.countDown()
  }

  private def doStop(): Unit = {
    Option(contextRef.get()) match {
      case Some(_) =>
        import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._

        logger.info("The app is going to shutdown...")
        logger.info(s"Going to stop ${components.size -> "count" -> null}, ${components.map(_.getClass).niceList() -> "components"}")

        val (stopped, failed) = components
          .map(s => s -> Try(s.stop()))
          .partition(_._2.isSuccess)
        logger.info(s"Service shutdown: ${stopped.size -> "stopped"} ; ${failed.size -> "failed to stop"}")

        val toClose = closeables.filterNot(_.isInstanceOf[RoleAppComponent])
        logger.info(s"Going to close ${toClose.size -> "count" -> null} ${toClose.map(_.getClass).niceList() -> "closeables"}")

        val (closed, failedToClose) = toClose
          .map(c => c -> Try(c.close()))
          .partition(_._2.isSuccess)
        logger.info(s"Service shutdown: ${closed.size -> "closed"} ; ${failedToClose.size -> "failed to close"}")

      case None =>
        logger.info("App isn't started yet")
    }
  }
}
