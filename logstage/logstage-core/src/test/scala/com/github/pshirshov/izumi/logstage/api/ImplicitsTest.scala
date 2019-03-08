package com.github.pshirshov.izumi.logstage.api

import com.github.pshirshov.izumi.functional.bio.SyncSafe2
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks._
import com.github.pshirshov.izumi.logstage.api.ImplicitsTest.Suspend2
import logstage.{LogBIO, LogIO}
import org.scalatest.WordSpec

class ImplicitsTest extends WordSpec {

  "create LogIO from Sync" in {
//    val log: LogIO[cats.effect.IO] = LogIO.fromLogger(IzLogger())
    val log: LogIO[cats.effect.IO] = LogIO.fromLogger[cats.effect.IO](IzLogger())
    log.discard()
  }

  "create LogBIO from BIO" in {
    val log: LogBIO[scalaz.zio.IO] = LogBIO.fromLogger(IzLogger())
    log.discard()
  }

  "LogBIO to LogIO covariance [when partially annotated]" in {
    implicit val log0: LogBIO[Suspend2] = LogBIO.fromLogger(IzLogger())

    for {
      _ <- logIO()
      _ <- logThrowable[Suspend2]()
      _ <- logIO()(log0)
      _ <- logThrowable[Suspend2]()(log0)
      _ <- expectThrowable[Suspend2](log0.info(""))
    } yield ()
  }

  "LogBIO to LogIO covariance [when not annotated]" in {
    implicit val log0: LogBIO[Suspend2] = LogBIO.fromLogger(IzLogger())

    for {
      _ <- logIO()
      _ <- logThrowable()
      _ <- logIO()(log0)
      _ <- logThrowable()(log0)
      _ <- expectThrowable(log0.info(""))
    } yield ()
  }

  def logIO[F[_]: LogIO](): F[Unit] = LogIO[F].info("abc")
  def logThrowable[F[+_, _]]()(implicit f: LogIO[F[Throwable, ?]]): F[Throwable, Unit] = f.info("cba")
  def expectThrowable[F[+_, _]](f: F[Throwable, Unit]): F[Throwable, Unit] = f

}

object ImplicitsTest {
  final case class Suspend2[+E, A](f: () => Either[E, A]) {
    def map[B](g: A => B): Suspend2[E, B] = {
      Suspend2(() => f().map(g))
    }
    def flatMap[E1 >: E, B](g: A => Suspend2[E1, B]): Suspend2[E1, B] = {
      Suspend2(() => f().flatMap(g(_).f()))
    }
  }
  object Suspend2 {
    implicit val syncSafeInstance: SyncSafe2[Suspend2] =
      new SyncSafe2[Suspend2] {
      override def syncSafe[A](unexceptionalEff: => A): Suspend2[Nothing, A] = {
        Suspend2(() => Right(unexceptionalEff))
      }
    }
  }
}
