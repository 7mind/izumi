package com.github.pshirshov.izumi.functional.bio

import scalaz.zio.IO

trait BlockingIO[F[_, _]] {

  /** Execute a blocking action in `Unyielding` thread pool, current task will be safely parked until the blocking task finishes **/
  def shiftBlocking[E, A](f: F[E ,A]): F[E, A]

  /** Execute a blocking impure task in `Unyielding` thread pool, current task will be safely parked until the blocking task finishes **/
  def syncBlocking[A](f: => A): F[Throwable, A]

  /** Execute a blocking impure task in `Unyielding` thread pool, current task will be safely parked until the blocking task finishes
    *
    * If canceled, the task will be killed via [[Thread.interrupt]] **/
  def syncInterruptibleBlocking[A](f: => A): F[Throwable, A]

}

object BlockingIO {
  def apply[F[_, _]: BlockingIO]: BlockingIO[F] = implicitly

  implicit final val blockingIOZIO: BlockingIO[IO] = new BlockingIO[IO] {
    override def shiftBlocking[E, A](f: IO[E ,A]): IO[E, A] = f.unyielding
    override def syncBlocking[A](f: => A): IO[Throwable, A] = IO.syncThrowable(f).unyielding
    override def syncInterruptibleBlocking[A](f: => A): IO[Throwable, A] = IO.blocking(f)
  }

}
