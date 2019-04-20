package com.github.pshirshov.izumi.distage.roles.services

import java.util.concurrent.{ExecutorService, TimeUnit}

import com.github.pshirshov.izumi.distage.model.definition.DIResource
import com.github.pshirshov.izumi.distage.model.monadic.DIEffect
import com.github.pshirshov.izumi.fundamentals.platform.functional.Identity
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks
import com.github.pshirshov.izumi.logstage.api.IzLogger
import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._

import scala.util.Try


object Finalizers {
  type CloseablesFinalized = CloseablesFinalized.type
  object CloseablesFinalized

  type ExecutorsFinalized = ExecutorsFinalized.type
  object ExecutorsFinalized

  class CloseablesFinalizer(logger: IzLogger, closeables: Set[AutoCloseable], executors: Set[ExecutorService]) extends DIResource[Identity, CloseablesFinalized] {
    override def acquire: CloseablesFinalized = DIEffect[Identity].maybeSuspend(CloseablesFinalized)

    override def release(resource: CloseablesFinalized): Unit = {
      Quirks.discard(resource)
      closeCloseables()
    }

    private def closeCloseables(): Unit = {
      val toClose = closeables
        .toList.reverse

      logger.info(s"Going to close ${toClose.size -> "count" -> null} ${toClose.map(_.getClass).niceList() -> "closeables"}")

      val (closed, failedToClose) = toClose
        .map(c => c -> Try(c.close()))
        .partition(_._2.isSuccess)
      logger.info(s"Service shutdown: ${closed.size -> "closed"} ; ${failedToClose.size -> "failed to close"}")
    }
  }


  class ExecutorsFinalizer(logger: IzLogger, closeables: Set[AutoCloseable], executors: Set[ExecutorService]) extends DIResource[Identity, ExecutorsFinalized] {
    override def acquire: ExecutorsFinalized = DIEffect[Identity].maybeSuspend(ExecutorsFinalized)

    override def release(resource: ExecutorsFinalized): Unit = {
      Quirks.discard(resource)
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

  }

}
