package com.github.pshirshov.izumi.logstage.api

import com.github.pshirshov.izumi.functional.bio.SyncSafe2
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks._
import com.github.pshirshov.izumi.logstage.api.ImplicitsTest.Suspend
import logstage.{LogBIO, LogIO}
import org.scalatest.WordSpec

class ImplicitsTest extends WordSpec {

  "create LogIO from Sync" in {
    val log: LogIO[cats.effect.IO] = LogIO.fromLogger(IzLogger())
    log.discard()
  }

  "create LogBIO from BIO" in {
    val log: LogBIO[scalaz.zio.IO] = LogBIO.fromLogger(IzLogger())
    log.discard()
  }

  "LogBIO to LogIO covariance" in {
//    val value: SyncSafe2[Suspend] = SyncSafe.covariance(SyncSafe.fromBIO(BIOZio))
//    implicit val log0: LogBIO[Suspend] = LogBIO.fromLogger[Suspend](IzLogger())(value)
    implicit val log0: LogBIO[Suspend] = LogBIO.fromLogger(IzLogger())
    logIO()
    logThrowable()
    logIO()(log0)
    logThrowable[Suspend]()(log0)
    expectThrowable[Suspend](log0.info(""))
  }

  def logIO[F[_]: LogIO](): F[Unit] = LogIO[F].info("abc")
  def logThrowable[F[_, _]]()(implicit f: LogIO[F[Throwable, ?]]): Unit = f.discard()
  def expectThrowable[F[_, _]](f: F[Throwable, Unit]): Unit = f.discard()

}

object ImplicitsTest {
  final case class Suspend[+E, A](f: () => Either[E, A])
  object Suspend {
    implicit val syncSafe: SyncSafe2[Suspend] =
      new SyncSafe2[Suspend] {
      override def syncSafe[A](unexceptionalEff: => A): Suspend[Nothing, A] = {
        Suspend(() => Right(unexceptionalEff))
      }
    }
  }
}
