package izumi.fundamentals.platform.files

import izumi.functional.quasi.{QuasiAsync, QuasiIO, QuasiTemporal}
import izumi.fundamentals.platform.functional.Identity

import scala.concurrent.{Await, Future, TimeoutException}
import scala.concurrent.duration.*
import scala.io.StdIn

object FileLockMutexProcess {
  def main(args: Array[String]): Unit = {
    require(args.length == 2, "expected two mutex names")
    val own = args(0)
    val other = args(1)
    val retryWait = 10.millis
    val watchdog = 10.seconds
    val timer = new QuasiTemporal[Identity] {
      override def sleep(duration: FiniteDuration): Unit = {
        println("SLEEP")
        QuasiTemporal.quasiTimerIdentity.sleep(duration)
      }
    }

    def mutex(name: String, async: QuasiAsync[Identity]) = FileLockMutex.allocate[Identity, Unit](
      filename = name,
      retryWait = retryWait,
      maxAttempts = 0,
      attemptLog = (_, _) => (),
      lockAlreadyExistedLog = (),
    )(
      attempts => throw new IllegalStateException(s"unexpected exhaustion: $attempts"),
      _ => (),
    )(QuasiIO.quasiIOIdentity, async, timer)

    val (_, unlock) = mutex(own, QuasiAsync.quasiAsyncIdentity).unsafeAllocate()
    var held = true
    var contender = Option.empty[Thread]
    def releaseHeld(): Unit = if (held) {
      unlock()
      held = false
      println("RELEASED")
    }

    try {
      println("HELD")
      var running = true
      while (running) {
        StdIn.readLine() match {
          case "WAIT" =>
            require(contender.isEmpty, "duplicate WAIT")
            val thread = new Thread(() => {
              try {
                val observed = new OnPendingFileLock[Identity](future => {
                  try { val _ = Await.ready(future, 1.second) }
                  catch { case _: TimeoutException => println("PENDING") }
                })
                mutex(other, observed).use(_ => ())
                println("COMPLETE")
              } catch {
                case error: Throwable =>
                  println(s"ERROR $error")
                  error.printStackTrace(Console.err)
              }
            }, "mutex-contender")
            contender = Some(thread)
            thread.start()
          case "RELEASE" => releaseHeld()
          case "EXIT" | null => running = false
          case command => throw new IllegalArgumentException(s"unknown command: $command")
        }
      }
    } finally {
      releaseHeld()
      contender.foreach { thread =>
        thread.join(watchdog.toMillis)
        require(!thread.isAlive, "contender did not finish")
      }
      Console.out.flush()
    }
  }
}

private[files] final class OnPendingFileLock[F[_]](pending: Future[?] => Unit)(implicit delegate: QuasiAsync[F]) extends QuasiAsync[F] {
  override def fromFuture[A](effect: => Future[A]): F[A] = delegate.fromFuture {
    val future = effect
    if (!future.isCompleted) pending(future)
    future
  }

  override def async[A](effect: (Either[Throwable, A] => Unit) => Unit): F[A] = delegate.async[A](effect)
  override def parTraverse[A, B](l: IterableOnce[A])(f: A => F[B]): F[List[B]] = delegate.parTraverse(l)(f)
  override def parTraverse_[A](l: IterableOnce[A])(f: A => F[Unit]): F[Unit] = delegate.parTraverse_(l)(f)
  override def parTraverseN[A, B](n: Int)(l: IterableOnce[A])(f: A => F[B]): F[List[B]] = delegate.parTraverseN(n)(l)(f)
  override def parTraverseN_[A](n: Int)(l: IterableOnce[A])(f: A => F[Unit]): F[Unit] = delegate.parTraverseN_(n)(l)(f)
}
