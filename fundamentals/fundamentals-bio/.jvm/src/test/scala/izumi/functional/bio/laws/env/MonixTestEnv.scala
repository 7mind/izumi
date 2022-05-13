package izumi.functional.bio.laws.env

import java.io.{ByteArrayOutputStream, PrintStream}

import cats.Eq
import cats.effect.laws.discipline.Parameters
import cats.effect.laws.discipline.arbitrary.{catsEffectLawsArbitraryForIO, catsEffectLawsCogenForIO}
import cats.effect.{ContextShift, IO => CIO}
import monix.bio.{Cause, IO, Task, UIO}
import monix.execution.atomic.Atomic
import monix.execution.exceptions.DummyException
import monix.execution.schedulers.TestScheduler
import monix.execution.{Cancelable, CancelableFuture, Scheduler}
import org.scalacheck.Arbitrary.{arbitrary => getArbitrary}
import org.scalacheck.{Arbitrary, Cogen, Gen}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.control.NonFatal
import scala.util.{Either, Failure, Success, Try}

trait MonixTestEnv extends BaseLawsSuite {
  implicit lazy val opt: IO.Options = IO.defaultOptions
  implicit lazy val cs: ContextShift[Task] = IO.contextShift
}

// Copypaste from monix-bio test suite
// https://github.com/monix/monix-bio/blob/c1b6e6b/core/shared/src/test/scala/monix/bio/BaseLawsSuite.scala

/**
  * Base trait to inherit in all `monix-bio` tests that use ScalaCheck.
  */
trait BaseLawsSuite extends ArbitraryInstances {

  /**
    * Customizes Cats-Effect's default params.
    *
    * At the moment of writing, these match the defaults, but it's
    * better to specify these explicitly.
    */
  implicit val params: Parameters = Parameters.default

//  override lazy val checkConfig: Parameters =
//    org.scalacheck.Test.Parameters.default
//      .withMinSuccessfulTests(if (isJVM) 100 else 10)
//      .withMaxDiscardRatio(if (isJVM) 5.0f else 50.0f)

//  lazy val slowCheckConfig: Parameters =
//    org.scalacheck.Test.Parameters.default
//      .withMinSuccessfulTests(10)
//      .withMaxDiscardRatio(50.0f)
//      .withMaxSize(6)
}

trait ArbitraryInstances extends ArbitraryInstancesBase {

  implicit def equalityIO[E, A](
    implicit
    A: Eq[A],
    E: Eq[E],
    sc: TestScheduler,
    opts: IO.Options,
  ): Eq[IO[E, A]] = {

    new Eq[IO[E, A]] {
      def eqv(lh: IO[E, A], rh: IO[E, A]): Boolean =
        equalityFutureEither(A, E, sc).eqv(lh.attempt.runToFutureOpt, rh.attempt.runToFutureOpt)
    }
  }

  implicit def equalityUIO[A](
    implicit
    A: Eq[A],
    sc: TestScheduler,
    opts: IO.Options,
  ): Eq[UIO[A]] = {

    new Eq[UIO[A]] {
      def eqv(lh: UIO[A], rh: UIO[A]): Boolean =
        equalityFuture(A, sc).eqv(
          lh.runToFutureOpt,
          rh.runToFutureOpt,
        )
    }
  }

  implicit def equalityTaskPar[E, A](
    implicit
    A: Eq[A],
    E: Eq[E],
    ec: TestScheduler,
    opts: IO.Options,
  ): Eq[IO.Par[E, A]] = {
    new Eq[IO.Par[E, A]] {
      import IO.Par.unwrap
      def eqv(lh: IO.Par[E, A], rh: IO.Par[E, A]): Boolean =
        Eq[IO[E, A]].eqv(unwrap(lh), unwrap(rh))
    }
  }

  implicit def equalityCIO[A](implicit A: Eq[A], ec: TestScheduler): Eq[CIO[A]] =
    new Eq[CIO[A]] {

      def eqv(x: CIO[A], y: CIO[A]): Boolean =
        equalityFuture[A].eqv(x.unsafeToFuture(), y.unsafeToFuture())
    }
}

trait ArbitraryInstancesBase extends ArbitraryInstances0 {

  implicit def arbitraryTask[E: Arbitrary, A: Arbitrary: Cogen]: Arbitrary[IO[E, A]] = {
    def genPure: Gen[IO[E, A]] =
      getArbitrary[A].map(IO.pure)

    def genEvalAsync: Gen[IO[E, A]] =
      getArbitrary[A].map(UIO.evalAsync(_))

    def genEval: Gen[IO[E, A]] =
      Gen.frequency(
        1 -> getArbitrary[A].map(IO.evalTotal(_)),
        1 -> getArbitrary[A].map(UIO(_)),
      )

    def genError: Gen[IO[E, A]] =
      getArbitrary[E].map(IO.raiseError)

    def genTerminate: Gen[IO[E, A]] =
      getArbitrary[Throwable].map(IO.terminate)

    def genAsync: Gen[IO[E, A]] =
      getArbitrary[(Either[Cause[E], A] => Unit) => Unit].map(a => IO.async(a))

    def genCancelable: Gen[IO[E, A]] =
      for (a <- getArbitrary[A]) yield IO.cancelable0[E, A] {
        (sc, cb) =>
          val isActive = Atomic(true)
          sc.execute {
            () =>
              if (isActive.getAndSet(false))
                cb.onSuccess(a)
          }
          UIO.eval(isActive.set(false))
      }

    def genNestedAsync: Gen[IO[E, A]] =
      getArbitrary[(Either[Cause[E], IO[E, A]] => Unit) => Unit]
        .map(k => IO.async(k).flatMap(x => x))

    def genBindSuspend: Gen[IO[E, A]] =
      getArbitrary[A].map(UIO.evalAsync(_).flatMap(IO.pure))

    def genSimpleTask =
      Gen.frequency(
        1 -> genPure,
        1 -> genEval,
        1 -> genEvalAsync,
        1 -> genError,
        1 -> genTerminate,
        1 -> genAsync,
        1 -> genNestedAsync,
        1 -> genBindSuspend,
      )

    def genContextSwitch: Gen[IO[E, A]] =
      for (t <- genSimpleTask) yield {
        t <* IO.shift
      }

    def genFlatMap: Gen[IO[E, A]] =
      for {
        ioa <- genSimpleTask
        f <- getArbitrary[A => IO[E, A]]
      } yield ioa.flatMap(f)

    def getMapOne: Gen[IO[E, A]] =
      for {
        ioa <- genSimpleTask
        f <- getArbitrary[A => A]
      } yield ioa.map(f)

    def getMapTwo: Gen[IO[E, A]] =
      for {
        ioa <- genSimpleTask
        f1 <- getArbitrary[A => A]
        f2 <- getArbitrary[A => A]
      } yield ioa.map(f1).map(f2)

    Arbitrary(
      Gen.frequency(
        1 -> genPure,
        1 -> genEvalAsync,
        1 -> genEval,
        1 -> genError,
        1 -> genTerminate,
        1 -> genContextSwitch,
        1 -> genCancelable,
        1 -> genBindSuspend,
        1 -> genAsync,
        1 -> genNestedAsync,
        1 -> getMapOne,
        1 -> getMapTwo,
        2 -> genFlatMap,
      )
    )
  }

  implicit def arbitraryUIO[A: Arbitrary: Cogen]: Arbitrary[UIO[A]] = {
    Arbitrary(getArbitrary[A].map(UIO(_)))
  }

  implicit def arbitraryCause[E: Arbitrary]: Arbitrary[Cause[E]] = {
    Arbitrary {
      implicitly[Arbitrary[Either[Throwable, E]]].arbitrary.map {
        case Left(value) => Cause.Termination(value)
        case Right(value) => Cause.Error(value)
      }
    }
  }

  implicit def arbitraryUIOf[A: Arbitrary: Cogen, B: Arbitrary: Cogen]: Arbitrary[A => UIO[B]] = {
    Arbitrary(getArbitrary[A => B].map(f => a => UIO(f(a))))
  }

  implicit def arbitraryTaskPar[E: Arbitrary, A: Arbitrary: Cogen]: Arbitrary[IO.Par[E, A]] =
    Arbitrary(arbitraryTask[E, A].arbitrary.map(IO.Par(_)))

  implicit def arbitraryIO[A: Arbitrary: Cogen]: Arbitrary[CIO[A]] =
    catsEffectLawsArbitraryForIO

  implicit def arbitraryExToA[A](implicit A: Arbitrary[A]): Arbitrary[Throwable => A] =
    Arbitrary {
      val fun = implicitly[Arbitrary[Int => A]]
      for (f <- fun.arbitrary) yield (t: Throwable) => f(t.hashCode())
    }

  implicit def arbitraryPfExToA[A](implicit A: Arbitrary[A]): Arbitrary[PartialFunction[Throwable, A]] =
    Arbitrary {
      val fun = implicitly[Arbitrary[Int => A]]
      for (f <- fun.arbitrary) yield { case (t: Throwable) => f(t.hashCode()) }
    }

  implicit def arbitraryTaskToLong[A, B](implicit B: Arbitrary[B]): Arbitrary[Task[A] => B] =
    Arbitrary {
      for (b <- B.arbitrary) yield (_: Task[A]) => b
    }

  implicit def arbitraryIOToLong[A, B](implicit B: Arbitrary[B]): Arbitrary[CIO[A] => B] =
    Arbitrary {
      for (b <- B.arbitrary) yield (_: CIO[A]) => b
    }

  implicit def cogenForTask[E, A]: Cogen[IO[E, A]] =
    Cogen[Unit].contramap(_ => ())

  implicit def cogenForIO[A: Cogen]: Cogen[CIO[A]] =
    catsEffectLawsCogenForIO
}

trait ArbitraryInstances0 extends ArbitraryInstancesBase0 {

  implicit def equalityCancelableFuture[E, A](
    implicit
    A: Eq[A],
    E: Eq[E],
    ec: TestScheduler,
  ): Eq[CancelableFuture[Either[E, A]]] =
    new Eq[CancelableFuture[Either[E, A]]] {
      val inst = equalityFutureEither[E, A]

      def eqv(x: CancelableFuture[Either[E, A]], y: CancelableFuture[Either[E, A]]) =
        inst.eqv(x, y)
    }

  implicit def arbitraryCancelableFuture[A](implicit A: Arbitrary[A], ec: Scheduler): Arbitrary[CancelableFuture[A]] =
    Arbitrary {
      for {
        a <- A.arbitrary
        future <- Gen.oneOf(
          CancelableFuture.pure(a),
          CancelableFuture.raiseError(DummyException(a.toString)),
          CancelableFuture.async[A](cb => { cb(Success(a)); Cancelable.empty }),
          CancelableFuture.async[A](cb => { cb(Failure(DummyException(a.toString))); Cancelable.empty }),
          CancelableFuture.pure(a).flatMap(CancelableFuture.pure),
        )
      } yield future
    }

  implicit def arbitraryThrowable: Arbitrary[Throwable] =
    Arbitrary {
      val msg = implicitly[Arbitrary[Int]]
      for (a <- msg.arbitrary) yield DummyException(a.toString)
    }

  implicit def cogenForCancelableFuture[A]: Cogen[CancelableFuture[A]] =
    Cogen[Unit].contramap(_ => ())
}

trait ArbitraryInstancesBase0 extends EqThrowable with TestUtils {

  implicit def equalityFutureEither[E, A](implicit A: Eq[A], E: Eq[E], ec: TestScheduler): Eq[Future[Either[E, A]]] =
    new Eq[Future[Either[E, A]]] {

      def eqv(x: Future[Either[E, A]], y: Future[Either[E, A]]): Boolean = {
        silenceSystemErr {
          // Executes the whole pending queue of runnables
          ec.tick(1.day, maxImmediateTasks = Some(500000))
          x.value match {
            case None =>
              y.value.isEmpty
            case Some(Success(Right(a))) =>
              y.value match {
                case Some(Success(Right(b))) => A.eqv(a, b)
                case _ => false
              }
            case Some(Success(Left(a))) =>
              y.value match {
                case Some(Success(Left(b))) => a.isInstanceOf[Throwable] && b.isInstanceOf[Throwable] || E.eqv(a, b)
                case Some(Failure(ex)) => a.isInstanceOf[Throwable] && a.getClass == ex.getClass
                case _ => false
              }
            case Some(Failure(ex)) =>
              y.value match {
                case Some(Failure(_)) =>
                  // Exceptions aren't values, it's too hard to reason about
                  // throwable equality and all exceptions are essentially
                  // yielding non-terminating futures and tasks from a type
                  // theory point of view, so we simply consider them all equal
                  true
                case Some(Success(Left(b))) => b.isInstanceOf[Throwable] && b.getClass == ex.getClass
                case _ =>
                  false
              }
          }
        }
      }
    }

  def equalityFuture[A](implicit A: Eq[A], ec: TestScheduler): Eq[Future[A]] =
    new Eq[Future[A]] {

      def eqv(x: Future[A], y: Future[A]): Boolean = {
        silenceSystemErr {
          // Executes the whole pending queue of runnables
          ec.tick(1.day, maxImmediateTasks = Some(500000))

          x.value match {
            case None =>
              y.value.isEmpty
            case Some(Success(a)) =>
              y.value match {
                case Some(Success(b)) => A.eqv(a, b)
                case _ => false
              }
            case Some(Failure(_)) =>
              y.value match {
                case Some(Failure(_)) =>
                  // Exceptions aren't values, it's too hard to reason about
                  // throwable equality and all exceptions are essentially
                  // yielding non-terminating futures and tasks from a type
                  // theory point of view, so we simply consider them all equal
                  true
                case _ =>
                  false
              }
          }
        }
      }
    }

  implicit def equalityTry[A: Eq]: Eq[Try[A]] =
    new Eq[Try[A]] {
      val optA = implicitly[Eq[Option[A]]]

      def eqv(x: Try[A], y: Try[A]): Boolean =
        if (x.isSuccess) optA.eqv(x.toOption, y.toOption)
        else y.isFailure
    }

  implicit def cogenForThrowable: Cogen[Throwable] =
    Cogen[String].contramap(_.toString)

  implicit def cogenForFuture[A]: Cogen[Future[A]] =
    Cogen[Unit].contramap(_ => ())
}

/**
  * INTERNAL API — test utilities.
  */
trait TestUtils {

  /**
    * Silences `System.err`, only printing the output in case exceptions are
    * thrown by the executed `thunk`.
    */
  def silenceSystemErr[A](thunk: => A): A =
    synchronized {
      // Silencing System.err
      val oldErr = System.err
      val outStream = new ByteArrayOutputStream
      val fakeErr = new PrintStream(outStream)
      System.setErr(fakeErr)
      try {
        val result = thunk
        System.setErr(oldErr)
        result
      } catch {
        case NonFatal(e) =>
          System.setErr(oldErr)
          // In case of errors, print whatever was caught
          fakeErr.close()
          val out = outStream.toString("utf-8")
          if (out.nonEmpty) oldErr.println(out)
          throw e
      }
    }

}
