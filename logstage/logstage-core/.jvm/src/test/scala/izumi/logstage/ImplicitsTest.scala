package izumi.logstage

import cats.effect.kernel.Sync
import izumi.functional.bio.{IO2, SyncSafe1, SyncSafe2}
import izumi.fundamentals.platform.language.IzScala
import izumi.fundamentals.platform.language.ScalaRelease
import izumi.fundamentals.platform.language.Quirks.*
import izumi.logstage.ImplicitsTest.Suspend2
import izumi.logstage.api.IzLogger
import logstage.{LogIO, LogIO2}
import org.scalatest.exceptions.TestFailedException
import org.scalatest.wordspec.AnyWordSpec

class ImplicitsTest extends AnyWordSpec {

  "create LogIO from IO and Sync" in {
    val log: LogIO[cats.effect.IO] = LogIO.fromLogger(IzLogger())
    log.discard()

    def logIO[F[_]: Sync]: LogIO[F] = LogIO.fromLogger(IzLogger())

    logIO[cats.effect.IO]
  }

  "progression test: can't create LogIO from covariant F/Sync even when annotated (FIXED in 2.13, but not in 2.12 -Xsource:2.13)" in {
    def test() = {
      assertCompiles("""
        def logIOC[F[+_]: Sync]: LogIO[F] = LogIO.fromLogger[F](IzLogger())
        logIOC[cats.effect.IO]
      """)
    }
    IzScala.scalaRelease match {
      case _: ScalaRelease.`2_12` => intercept[TestFailedException](test())
      case _ => test()
    }
  }

  "create LogIO2 from BIO" in {
    val log: LogIO2[zio.IO] = LogIO2.fromLogger(IzLogger())
    log.discard()

    def logIO[F[+_, +_]: IO2]: LogIO2[F] = LogIO.fromLogger(IzLogger())

    logIO[zio.IO]

    def logBIO[F[+_, +_]: IO2]: LogIO2[F] = LogIO2.fromLogger(IzLogger())

    logBIO[zio.IO]
  }

  "create LogIO2 from SyncSafe" in {
    val log: LogIO2[Suspend2] = LogIO2.fromLogger(IzLogger())
    log.discard()

    def logIO[F[+_, +_]: SyncSafe2]: LogIO2[F] = LogIO.fromLogger(IzLogger())

    logIO[Suspend2]

    def logBIO[F[+_, +_]: SyncSafe2]: LogIO2[F] = LogIO2.fromLogger(IzLogger())

    logBIO[Suspend2]
  }

  "LogIO2 to LogIO covariance works when partially annotated" in {
    implicit val log0: LogIO2[Suspend2] = LogIO2.fromLogger[Suspend2](IzLogger())

    for {
      _ <- logIO()
      _ <- logThrowable[Suspend2]()
      _ <- syncSafeLogThrowable[Suspend2]()
      _ <- logIO()(log0)
      _ <- logThrowable[Suspend2]()(log0)
      _ <- syncSafeLogThrowable[Suspend2]()(Suspend2.syncSafeInstance)
      _ <- expectThrowable[Suspend2](log0.info(""))
    } yield ()
  }

  "progression test: LogIO2 to LogIO covariance fails when not annotated" in {
    implicit val log0: LogIO2[Suspend2] = LogIO2.fromLogger(IzLogger())
    log0.discard()

    assertTypeError("""
    for {
      _ <- logIO()
      _ <- logThrowable()
      _ <- logIO()(log0)
      _ <- logThrowable()(log0)
      _ <- expectThrowable(log0.info(""))
    } yield ()
      """)
  }

  def logIO[F[_]: LogIO](): F[Unit] = LogIO[F].info("abc")

  def logThrowable[F[+_, _]]()(implicit f: LogIO[F[Throwable, _]]): F[Throwable, Unit] = f.info("cba")

  def syncSafeLogThrowable[F[+_, _]]()(implicit f: SyncSafe1[F[Throwable, _]]): F[Throwable, Unit] = LogIO.fromLogger(IzLogger()).info("cba")

  def expectThrowable[F[+_, _]](f: F[Throwable, Unit]): F[Throwable, Unit] = f

}

object ImplicitsTest {

  final case class Suspend2[+E, +A](f: () => Either[E, A]) {
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
