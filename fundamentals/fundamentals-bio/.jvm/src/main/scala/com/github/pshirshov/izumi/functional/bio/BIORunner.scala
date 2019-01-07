package com.github.pshirshov.izumi.functional.bio

import java.util.concurrent.ExecutorService

import scalaz.zio.ExitResult.Cause
import scalaz.zio._

trait BIORunner[F[_, _]] {
  def unsafeRun[E, A](io: F[E, A]): A

  def unsafeRunSyncAsEither[E, A](io: F[E, A]): BIOExit[E, A]

  def unsafeRunAsyncAsEither[E, A](io: F[E, A])(callback: BIOExit[E, A] => Unit): Unit
}

object BIORunner {
  def apply[F[_, _] : BIORunner]: BIORunner[F] = implicitly

  def createZIO(threadPool: ExecutorService, handler: DefaultHandler = DefaultHandler.Default): BIORunner[IO] = new ZIORunnerBase(threadPool, handler)

  sealed trait DefaultHandler

  object DefaultHandler {

    case object Default extends DefaultHandler

    case class Custom(handler: BIOExit.Failure[Any] => IO[Nothing, Unit]) extends DefaultHandler

  }

  class ZIORunnerBase(override val threadPool: ExecutorService, handler: DefaultHandler)
    extends BIORunner[IO]
      with RTS {

    override def defaultHandler: ExitResult.Cause[Any] => IO[Nothing, Unit] = {
      handler match {
        case DefaultHandler.Default =>
          super.defaultHandler

        case DefaultHandler.Custom(f) =>
          cause: Cause[Any] => f(BIO.BIOZio.toBIOExit(cause))
      }
    }

    def unsafeRunAsyncAsEither[E, A](io: IO[E, A])(callback: BIOExit[E, A] => Unit): Unit = {
      unsafeRunAsync(io)(exitResult => callback(BIO.BIOZio.toBIOExit(exitResult)))
    }

    override def unsafeRunSyncAsEither[E, A](io: IO[E, A]): BIOExit[E, A] = {
      val result = unsafeRunSync(io)
      BIO.BIOZio.toBIOExit(result)
    }
  }
}
