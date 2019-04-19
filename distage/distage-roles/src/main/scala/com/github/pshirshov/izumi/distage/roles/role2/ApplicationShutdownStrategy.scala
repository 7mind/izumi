package com.github.pshirshov.izumi.distage.roles.role2

import java.util.concurrent.CountDownLatch

import com.github.pshirshov.izumi.distage.model.monadic.DIEffect
import com.github.pshirshov.izumi.fundamentals.platform.functional.Identity
import com.github.pshirshov.izumi.logstage.api.IzLogger


trait ApplicationShutdownStrategy[F[_]] {
  def await(logger: IzLogger): F[Unit]

  def release(): Unit
}

class JvmExitHookLatchShutdownStrategy extends ApplicationShutdownStrategy[Identity] {
  private val latch = new CountDownLatch(1)

  val mainLatch: CountDownLatch = new CountDownLatch(1)

  override def release(): Unit = mainLatch.countDown()

  def stop(): Unit = {
    latch.countDown()
    mainLatch.await() // we need to let main thread to finish everything
  }

  def await(logger: IzLogger): Identity[Unit] = {
    val shutdownHook = new Thread(() => {
      stop()
    }, "termination-hook")

    DIEffect[Identity].maybeSuspend {
      logger.info("Waiting on latch...")
      Runtime.getRuntime.addShutdownHook(shutdownHook)
      latch.await()
      try {
        Runtime.getRuntime.removeShutdownHook(shutdownHook)
      } catch {
        case _: IllegalStateException =>
      }
      logger.info("Going to shut down...")
    }
  }
}

class ImmediateExitShutdownStrategy[F[_] : DIEffect] extends ApplicationShutdownStrategy[F] {
  def await(logger: IzLogger): F[Unit] = {
    DIEffect[F].maybeSuspend {
      logger.info("Exiting immediately...")
    }
  }

  override def release(): Unit = {}
}
