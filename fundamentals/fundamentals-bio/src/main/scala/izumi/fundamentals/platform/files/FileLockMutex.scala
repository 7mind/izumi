package izumi.fundamentals.platform.files

import izumi.functional.bio.{Async2, Primitives2, Temporal2}
import izumi.functional.lifecycle.Lifecycle

import java.io.File
import java.nio.channels.{AsynchronousFileChannel, CompletionHandler, FileLock, OverlappingFileLockException}
import java.nio.file.StandardOpenOption
import scala.concurrent.duration.*

object FileLockMutex {

  def withLocalMutex[F[+_, +_], A](
    filename: String,
    retryWait: FiniteDuration,
    maxAttempts: Int,
    attemptLog: (Int, Int) => F[Throwable, Unit],
    lockAlreadyExistedLog: => F[Throwable, Unit],
  )(fail: Int => F[Throwable, A],
    succ: FileLock => F[Throwable, A],
  )(implicit
    IO: Async2[F],
    T: Temporal2[F],
    Prim: Primitives2[F],
  ): F[Throwable, A] = {
    // mergeAsyncPrimitives is called in `allocate`; here we only forward the same IO & Prim.
    allocate[F, A](filename, retryWait, maxAttempts, attemptLog, lockAlreadyExistedLog)(fail, succ).use[Throwable, A](IO.pure(_))(IO)
  }

  def allocate[F[+_, +_], A](
    filename: String,
    retryWait: FiniteDuration,
    maxAttempts: Int,
    attemptLog: (Int, Int) => F[Throwable, Unit],
    lockAlreadyExistedLog: => F[Throwable, Unit],
  )(fail: Int => F[Throwable, A],
    succ: FileLock => F[Throwable, A],
  )(implicit
    IO: Async2[F],
    T: Temporal2[F],
    Prim: Primitives2[F],
  ): Lifecycle[F, Throwable, A] = {
    implicit val ioPrim: Async2[F] & Primitives2[F] = mergeAsyncPrimitives[F](IO, Prim)

    def retryOnFileLock(
      doAcquire: => F[Throwable, FileLock]
    ): F[Throwable, (A, Option[FileLock])] = {
      IO.tailRecM(0) {
        attempts =>
          val ifNeeded: F[Throwable, Unit] = IO.when(attempts != 0)(attemptLog(attempts, maxAttempts))
          IO.flatMap[Throwable, Unit, Either[Int, (A, Option[FileLock])]](ifNeeded) {
            _ =>
              IO.redeem[Throwable, (A, Option[FileLock]), Throwable, Either[Int, (A, Option[FileLock])]](
                IO.flatMap(IO.suspendSafe(doAcquire))(lock => IO.map(succ(lock))(a => (a, Some(lock))))
              )(
                err = {
                  case _: OverlappingFileLockException =>
                    if (attempts < maxAttempts) {
                      IO.map[Throwable, Unit, Either[Int, (A, Option[FileLock])]](T.sleep(retryWait))(_ => Left(attempts + 1))
                    } else {
                      IO.map[Throwable, A, Either[Int, (A, Option[FileLock])]](fail(attempts))(a => Right((a, None)))
                    }
                  case other =>
                    IO.fail(other)
                },
                succ = result => IO.pure(Right(result)),
              )
          }
      }
    }

    def createChannel(): F[Throwable, AsynchronousFileChannel] = IO.suspendThrowable {
      val tmpDir = System.getProperty("java.io.tmpdir")
      val file = new File(s"$tmpDir/$filename.tmp")
      val newFileCreated = file.createNewFile()
      val log: F[Throwable, Unit] = if (newFileCreated) IO.sync(file.deleteOnExit()) else lockAlreadyExistedLog
      IO.flatMap[Throwable, Unit, AsynchronousFileChannel](log) {
        _ => IO.syncThrowable(AsynchronousFileChannel.open(file.toPath, StandardOpenOption.WRITE))
      }
    }

    def acquireLock(channel: AsynchronousFileChannel): F[Throwable, (A, Option[FileLock])] = {
      retryOnFileLock {
        IO.async[Throwable, FileLock] {
          cb =>
            val handler = new CompletionHandler[FileLock, Unit] {
              override def completed(result: FileLock, attachment: Unit): Unit = cb(Right(result))
              override def failed(exc: Throwable, attachment: Unit): Unit = cb(Left(exc))
            }
            channel.lock((), handler)
        }
      }
    }

    Lifecycle
      .make[F, Throwable, AsynchronousFileChannel](
        acquire = createChannel()
      )(release = {
        channel =>
          IO.catchAll[Throwable, Unit, Nothing](IO.syncThrowable(channel.close()))(_ => IO.unit)
      }).flatMap[Throwable, A] {
        channel =>
          Lifecycle
            .make[F, Throwable, (A, Option[FileLock])](
              acquire = acquireLock(channel)
            )(release = {
              case (_, Some(lock)) => IO.catchAll[Throwable, Unit, Nothing](IO.syncThrowable(lock.close()))(_ => IO.unit)
              case (_, None) => IO.unit
            }).map[A](_._1)
      }
  }

  /** Build a forwarder that satisfies `Async2[F] & Primitives2[F]` by delegating to the two
    * supplied dictionaries. Required because Scala 3 will not synthesize intersection
    * dictionaries automatically — every BIO method declared on the intersection has to be
    * forwarded explicitly to one half.
    */
  private def mergeAsyncPrimitives[F[+_, +_]](asyncDict: Async2[F], primDict: Primitives2[F]): Async2[F] & Primitives2[F] = {
    new Async2[F] with Primitives2[F] {
      override def InnerF: izumi.functional.bio.Panic2[F] = this

      override def async[E, A](register: (Either[E, A] => Unit) => Unit): F[E, A] = asyncDict.async(register)
      override def asyncF[E, A](register: (Either[E, A] => Unit) => F[E, Unit]): F[E, A] = asyncDict.asyncF(register)
      override def asyncWithOnInterrupt[E, A](
        register: (Either[E, A] => Unit) => izumi.functional.bio.data.InterruptAction[F]
      ): F[E, A] = asyncDict.asyncWithOnInterrupt(register)
      override def fromFuture[A](mkFuture: scala.concurrent.ExecutionContext => scala.concurrent.Future[A]): F[Throwable, A] = asyncDict.fromFuture(mkFuture)
      override def fromFutureJava[A](javaFuture: => java.util.concurrent.CompletionStage[A]): F[Throwable, A] = asyncDict.fromFutureJava(javaFuture)
      override def currentEC: F[Nothing, scala.concurrent.ExecutionContext] = asyncDict.currentEC
      override def onEC[E, A](ec: scala.concurrent.ExecutionContext)(f: F[E, A]): F[E, A] = asyncDict.onEC(ec)(f)
      override def never: F[Nothing, Nothing] = asyncDict.never
      override def yieldNow: F[Nothing, Unit] = asyncDict.yieldNow
      override def parTraverse[E, A, B](l: Iterable[A])(f: A => F[E, B]): F[E, List[B]] = asyncDict.parTraverse(l)(f)
      override def parTraverseN[E, A, B](maxConcurrent: Int)(l: Iterable[A])(f: A => F[E, B]): F[E, List[B]] = asyncDict.parTraverseN(maxConcurrent)(l)(f)
      override def parTraverseNCore[E, A, B](l: Iterable[A])(f: A => F[E, B]): F[E, List[B]] = asyncDict.parTraverseNCore(l)(f)
      override def zipWithPar[E, A, B, C](fa: F[E, A], fb: F[E, B])(f: (A, B) => C): F[E, C] = asyncDict.zipWithPar(fa, fb)(f)
      override def race[E, A](r1: F[E, A], r2: F[E, A]): F[E, A] = asyncDict.race(r1, r2)
      override def racePairUnsafe[E, A, B](fa: F[E, A], fb: F[E, B]): F[E, Either[
        (izumi.functional.bio.Exit[E, A], izumi.functional.bio.Fiber2[F, E, B]),
        (izumi.functional.bio.Fiber2[F, E, A], izumi.functional.bio.Exit[E, B]),
      ]] = asyncDict.racePairUnsafe(fa, fb)
      override def sync[A](effect: => A): F[Nothing, A] = asyncDict.sync(effect)
      override def syncThrowable[A](effect: => A): F[Throwable, A] = asyncDict.syncThrowable(effect)
      override def pure[A](a: A): F[Nothing, A] = asyncDict.pure(a)
      override def terminate(v: => Throwable): F[Nothing, Nothing] = asyncDict.terminate(v)
      override def sandbox[E, A](r: F[E, A]): F[izumi.functional.bio.Exit.FailureUninterrupted[E], A] = asyncDict.sandbox(r)
      override def sendInterruptToSelf: F[Nothing, Unit] = asyncDict.sendInterruptToSelf
      override def fail[E](v: => E): F[E, Nothing] = asyncDict.fail(v)
      override def catchAll[E, A, E2](r: F[E, A])(f: E => F[E2, A]): F[E2, A] = asyncDict.catchAll(r)(f)
      override def flatMap[E, A, B](r: F[E, A])(f: A => F[E, B]): F[E, B] = asyncDict.flatMap(r)(f)
      override def uninterruptibleExcept[E, A](
        f: izumi.functional.bio.data.RestoreInterruption2[F] => F[E, A]
      ): F[E, A] = asyncDict.uninterruptibleExcept(f)
      override def bracketCase[E, A, B](
        acquire: F[E, A]
      )(release: (A, izumi.functional.bio.Exit[E, B]) => F[Nothing, Unit]
      )(use: A => F[E, B]
      ): F[E, B] = asyncDict.bracketCase(acquire)(release)(use)
      override def map[E, A, B](r: F[E, A])(f: A => B): F[E, B] = asyncDict.map(r)(f)
      override def fromSandboxExit[E, A](effect: => izumi.functional.bio.Exit.Uninterrupted[E, A]): F[E, A] = asyncDict.fromSandboxExit(effect)

      // Primitives2
      override def mkRef[A](a: A): F[Nothing, izumi.functional.bio.Ref2[F, A]] = primDict.mkRef(a)
      override def mkPromise[E, A]: F[Nothing, izumi.functional.bio.Promise2[F, E, A]] = primDict.mkPromise[E, A]
      override def mkSemaphore(permits: Long): F[Nothing, izumi.functional.bio.Semaphore2[F]] = primDict.mkSemaphore(permits)
    }
  }

}
