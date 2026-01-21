package izumi.functional.bio.impl

import izumi.functional.bio.Exit.Trace
import izumi.functional.bio.data.{InterruptAction, Morphism2, RestoreInterruption2}
import izumi.functional.bio.impl.MiniBIOAsync.Fail
import izumi.functional.bio.{BlockingIO2, Exit, UnsafeRun2, WeakAsync2, WeakTemporal2}
import izumi.fundamentals.platform.language.Quirks.Discarder

import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}
import scala.annotation.tailrec
import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success}

/**
  * [[MiniBIO]] extended with support for async operations via the Async constructor.
  *
  * This effect type does not support interruption.
  *
  * Made for use in distage-testkit. Prefer ZIO or cats-bio in production.
  */
sealed trait MiniBIOAsync[+E, +A] {

  /**
    * Runs the effect synchronously until the first async boundary.
    * @return Left if the effect completes synchronously, or Right with a continuation if async execution is needed.
    */
  final def runSyncToFirstAsyncBoundary(): Either[Exit.Uninterrupted[E, A], ExecutionContext => Future[Exit.Uninterrupted[E, A]]] = {
    runSyncToFirstAsyncBoundaryImpl(MiniBIOAsync.AsyncInterrupt.Noop)
  }

  private[impl] final def runSyncToFirstAsyncBoundaryInterruptible(
    asyncInterrupt: MiniBIOAsync.AsyncInterruptRef
  ): Either[Exit.Uninterrupted[E, A], ExecutionContext => Future[Exit.Uninterrupted[E, A]]] = {
    runSyncToFirstAsyncBoundaryImpl(asyncInterrupt)
  }

  private def runSyncToFirstAsyncBoundaryImpl(
    asyncInterrupt: MiniBIOAsync.AsyncInterrupt
  ): Either[Exit.Uninterrupted[E, A], ExecutionContext => Future[Exit.Uninterrupted[E, A]]] = {
    final class Catcher[E0, A0, E1, B](
      val recover: Exit.FailureUninterrupted[E0] => MiniBIOAsync[E1, B],
      f: A0 => MiniBIOAsync[E1, B],
    ) extends (A0 => MiniBIOAsync[E1, B]) {
      override def apply(a: A0): MiniBIOAsync[E1, B] = f(a)
    }

    @tailrec def runner(
      op: MiniBIOAsync[Any, Any],
      stack: List[Any => MiniBIOAsync[Any, Any]],
    ): Either[Exit.Uninterrupted[Any, Any], ExecutionContext => Future[Exit.Uninterrupted[Any, Any]]] = op match {

      case MiniBIOAsync.FlatMap(io, f) =>
        runner(io, f.asInstanceOf[Any => MiniBIOAsync[Any, Any]] :: stack)

      case MiniBIOAsync.Redeem(io, err, succ) =>
        runner(io, new Catcher(err, succ).asInstanceOf[Any => MiniBIOAsync[Any, Any]] :: stack)

      case MiniBIOAsync.Sync(a) =>
        val exit =
          try { a() }
          catch {
            case t: Throwable =>
              Exit.Termination(t, Trace.ThrowableTrace(t))
          }
        exit match {
          case Exit.Success(value) =>
            stack match {
              case flatMap :: stackRest =>
                val nextIO =
                  try { flatMap(value) }
                  catch {
                    case t: Throwable =>
                      Fail.terminate(t)
                  }
                runner(nextIO, stackRest)

              case Nil =>
                Left(exit)
            }

          case failure: Exit.FailureUninterrupted[?] =>
            runner(Fail.halt(failure), stack)
        }

      case MiniBIOAsync.Fail(e) =>
        val err =
          try e()
          catch {
            case t: Throwable =>
              Exit.Termination(t, Trace.ThrowableTrace(t))
          }
        val catcher = stack.dropWhile(!_.isInstanceOf[Catcher[?, ?, ?, ?]])
        catcher match {
          case value :: stackRest =>
            runner(value.asInstanceOf[Catcher[Any, Any, Any, Any]].recover(err), stackRest)

          case Nil =>
            Left(err)
        }

      case MiniBIOAsync.Async(register) =>
        // Hit async boundary - return continuation
        Right {
          (ec: ExecutionContext) =>
            val resultPromise = Promise[Exit.Uninterrupted[Any, Any]]()
            val resumed = new AtomicBoolean(false)

            def continue(exit: Exit.Uninterrupted[Any, Any], resumeEc: ExecutionContext): Unit = {
              val next = exit match {
                case success @ Exit.Success(value) =>
                  stack match {
                    case flatMap :: stackRest =>
                      val nextIO =
                        try { flatMap(value) }
                        catch {
                          case t: Throwable =>
                            Fail.terminate(t)
                        }
                      runnerAsync(nextIO, stackRest, resumeEc)
                    case Nil =>
                      Future.successful(success)
                  }
                case failure: Exit.FailureUninterrupted[?] =>
                  runnerAsync(Fail.halt(failure), stack, resumeEc)
              }
              next.onComplete(resultPromise.tryComplete)(using resumeEc)
            }

            val interruptAction = () => {
              if (resumed.compareAndSet(false, true)) {
                continue(Exit.Termination.forThrowable(new InterruptedException), ec)
              }
              ()
            }
            asyncInterrupt.set(interruptAction)

            try {
              val callback = (exit: Exit.Uninterrupted[Any, Any]) => {
                if (resumed.compareAndSet(false, true)) {
                  continue(exit, ec)
                }
                ()
              }
              register(ec, callback)
            } catch {
              case t: Throwable =>
                if (resumed.compareAndSet(false, true)) {
                  continue(Exit.Termination.forThrowable(t), ec)
                }
            }

            resultPromise.future.onComplete(_ => asyncInterrupt.clear(interruptAction))(using ec)
            resultPromise.future
        }
    }

    def runnerAsync(op: MiniBIOAsync[Any, Any], stack: List[Any => MiniBIOAsync[Any, Any]], ec: ExecutionContext): Future[Exit.Uninterrupted[Any, Any]] = {
      runner(op, stack) match {
        case Left(earlyResult) => Future.successful(earlyResult)
        case Right(continuation) => continuation(ec)
      }
    }

    runner(this, Nil).asInstanceOf[Either[Exit.Uninterrupted[E, A], ExecutionContext => Future[Exit.Uninterrupted[E, A]]]]
  }

  /**
    * Runs the effect on the provided ExecutionContext
    * @note Even for synchronous effects, execution will be deferred to the EC.
    */
  final def runOnEC(ec: ExecutionContext): Future[Exit.Uninterrupted[E, A]] = {
    // Defer execution to EC to ensure true parallelism
    val promise = Promise[Exit.Uninterrupted[E, A]]()
    ec.execute(
      () => {
        runSyncToFirstAsyncBoundary() match {
          case Left(result) => promise.success(result)
          case Right(continuation) => continuation(ec).onComplete(promise.complete)(using ec)
        }
      }
    )
    promise.future
  }

  private[impl] final def runOnECInterruptible(ec: ExecutionContext): (Future[Exit.Uninterrupted[E, A]], InterruptAction[MiniBIOAsync]) = {
    val asyncInterrupt = new MiniBIOAsync.AsyncInterruptRef
    val promise = Promise[Exit.Uninterrupted[E, A]]()
    ec.execute(
      () => {
        if (asyncInterrupt.isInterrupted) {
          promise.success(Exit.Termination.forThrowable(new InterruptedException))
        } else {
          runSyncToFirstAsyncBoundaryInterruptible(asyncInterrupt) match {
            case Left(result) => promise.success(result)
            case Right(continuation) => continuation(ec).onComplete(promise.complete)(using ec)
          }
        }
      }
    )
    val interrupt = InterruptAction(MiniBIOAsync.WeakAsyncForMiniBIOAsync.sync(asyncInterrupt.interrupt()))
    (promise.future, interrupt)
  }

  /**
    * Runs the effect on current thread up to first async boundary and
    * then migrates execution to provided [[ExecutionContext]]
    * @return Completed future if there were no Async nodes, completable future otherwise
    */
  final def runSyncToFirstAsyncBoundaryOrOnEC(ec: ExecutionContext): Future[Exit.Uninterrupted[E, A]] = {
    runSyncToFirstAsyncBoundary() match {
      case Left(exit) => Future.successful(exit)
      case Right(mkFuture) => mkFuture(ec)
    }
  }

}

object MiniBIOAsync extends MiniBIOAsyncPlatformSpecific {
  private[impl] sealed trait AsyncInterrupt {
    def set(action: () => Unit): Unit
    def clear(action: () => Unit): Unit
  }
  private[impl] object AsyncInterrupt {
    object Noop extends AsyncInterrupt {
      override def set(action: () => Unit): Unit = ()
      override def clear(action: () => Unit): Unit = ()
    }
  }

  private[impl] final class AsyncInterruptRef extends AsyncInterrupt {
    private val noop: () => Unit = () => ()
    private val ref = new AtomicReference[() => Unit](noop)
    private val interrupted = new AtomicBoolean(false)
    override def set(action: () => Unit): Unit = {
      ref.set(action)
      if (interrupted.get()) {
        if (ref.compareAndSet(action, noop)) {
          action()
        }
      }
    }
    override def clear(action: () => Unit): Unit = {
      ref.compareAndSet(action, noop)
      interrupted.set(false)
    }
    def interrupt(): Unit = {
      interrupted.set(true)
      ref.getAndSet(noop).apply()
    }
    def isInterrupted: Boolean = interrupted.get()
  }

  final case class Fail[+E](e: () => Exit.FailureUninterrupted[E]) extends MiniBIOAsync[E, Nothing]
  object Fail {
    def terminate(t: Throwable): Fail[Nothing] = Fail(() => Exit.Termination(t, Trace.ThrowableTrace(t)))
    def halt[E](e: => Exit.FailureUninterrupted[E]): Fail[E] = Fail(() => e)
  }
  final case class Sync[+E, +A](a: () => Exit.Uninterrupted[E, A]) extends MiniBIOAsync[E, A]
  final case class FlatMap[E, A, +E1 >: E, +B](io: MiniBIOAsync[E, A], f: A => MiniBIOAsync[E1, B]) extends MiniBIOAsync[E1, B]
  final case class Redeem[E, A, +E1, +B](
    io: MiniBIOAsync[E, A],
    err: Exit.FailureUninterrupted[E] => MiniBIOAsync[E1, B],
    succ: A => MiniBIOAsync[E1, B],
  ) extends MiniBIOAsync[E1, B]
  final case class Async[+E, +A](register: (ExecutionContext, Exit.Uninterrupted[E, A] => Unit) => Unit) extends MiniBIOAsync[E, A]

  implicit object WeakAsyncForMiniBIOAsync extends WeakAsync2[MiniBIOAsync] with BlockingIO2[MiniBIOAsync] with WeakTemporal2[MiniBIOAsync] {
    override def pure[A](a: A): MiniBIOAsync[Nothing, A] = Sync(() => Exit.Success(a))
    override def flatMap[E, A, B](r: MiniBIOAsync[E, A])(f: A => MiniBIOAsync[E, B]): MiniBIOAsync[E, B] = FlatMap(r, f)
    override def fail[E](v: => E): MiniBIOAsync[E, Nothing] = Fail(() => Exit.Error.forTypedError(v))
    override def terminate(v: => Throwable): MiniBIOAsync[Nothing, Nothing] = Fail.terminate(v)
    override def sendInterruptToSelf: MiniBIOAsync[Nothing, Unit] = unit
    override def fromSandboxExit[E, A](effect: => Exit.Uninterrupted[E, A]): MiniBIOAsync[E, A] = Sync(() => effect)

    override def syncThrowable[A](effect: => A): MiniBIOAsync[Throwable, A] = Sync {
      () =>
        try {
          Exit.Success(effect)
        } catch { case e: Throwable => Exit.Error.forThrowable(e) }
    }
    override def sync[A](effect: => A): MiniBIOAsync[Nothing, A] = {
      Sync(() => Exit.Success(effect))
    }

    override def redeem[E, A, E2, B](r: MiniBIOAsync[E, A])(err: E => MiniBIOAsync[E2, B], succ: A => MiniBIOAsync[E2, B]): MiniBIOAsync[E2, B] = {
      Redeem[E, A, E2, B](
        r,
        {
          case e: Exit.Termination => Fail.halt(e)
          case Exit.Error(e, _) => err(e)
        },
        succ,
      )
    }

    override def catchAll[E, A, E2](r: MiniBIOAsync[E, A])(f: E => MiniBIOAsync[E2, A]): MiniBIOAsync[E2, A] = redeem(r)(f, pure)

    override def bracketCase[E, A, B](
      acquire: MiniBIOAsync[E, A]
    )(release: (A, Exit[E, B]) => MiniBIOAsync[Nothing, Unit]
    )(use: A => MiniBIOAsync[E, B]
    ): MiniBIOAsync[E, B] = {
      // does not propagate error raised in release if `use` failed, in that case only error from `use` is preserved
      flatMap(acquire)(
        a =>
          Redeem[E, B, E, B](
            io = use(a),
            err = e => Redeem[Nothing, Unit, E, Nothing](release(a, e), err = _ => Fail(() => e), succ = _ => Fail(() => e)),
            succ = v => map(release(a, Exit.Success(v)))(_ => v),
          )
      )
    }

    override def sandbox[E, A](r: MiniBIOAsync[E, A]): MiniBIOAsync[Exit.FailureUninterrupted[E], A] = {
      Redeem[E, A, Exit.FailureUninterrupted[E], A](r, e => fail(e), pure)
    }

    override def traverse[E, A, B](l: Iterable[A])(f: A => MiniBIOAsync[E, B]): MiniBIOAsync[E, List[B]] = {
      val x = l.foldLeft(pure(Nil): MiniBIOAsync[E, List[B]]) {
        (acc, a) =>
          flatMap(acc)(list => map(f(a))(_ :: list))
      }
      map(x)(_.reverse)
    }

    override def uninterruptible[E, A](f: MiniBIOAsync[E, A]): MiniBIOAsync[E, A] = f
    override def uninterruptibleExcept[E, A](f: RestoreInterruption2[MiniBIOAsync] => MiniBIOAsync[E, A]): MiniBIOAsync[E, A] = f(Morphism2.identity[MiniBIOAsync])
    override def bracketExcept[E, A, B](
      acquire: RestoreInterruption2[MiniBIOAsync] => MiniBIOAsync[E, A]
    )(release: (A, Exit[E, B]) => MiniBIOAsync[Nothing, Unit]
    )(use: A => MiniBIOAsync[E, B]
    ): MiniBIOAsync[E, B] = bracketCase(acquire(Morphism2.identity[MiniBIOAsync]))(release)(use)

    // BlockingIO2
    override def shiftBlocking[E, A](f: MiniBIOAsync[E, A]): MiniBIOAsync[E, A] = f
    override def syncInterruptibleBlocking[A](f: => A): MiniBIOAsync[Throwable, A] = syncBlocking(f)
    override def syncBlocking[A](f: => A): MiniBIOAsync[Throwable, A] = syncThrowable(scala.concurrent.blocking(f))

    // WeakAsync2
    override def async[E, A](register: (Either[E, A] => Unit) => Unit): MiniBIOAsync[E, A] = {
      Async[E, A] {
        (_, cb) =>
          register {
            case Right(v) => cb(Exit.Success(v))
            case Left(e) => cb(Exit.Error.forTypedError(e))
          }
      }
    }

    override def fromFuture[A](mkFuture: ExecutionContext => Future[A]): MiniBIOAsync[Throwable, A] = {
      Async[Throwable, A] {
        (ec, cb) =>
          mkFuture(ec).onComplete {
            case Success(v) => cb(Exit.Success(v))
            case Failure(e) => cb(Exit.Error.forThrowable(e))
          }(using ec)
      }
    }

    // Parallel2
    override def zipWithPar[E, A, B, C](fa: MiniBIOAsync[E, A], fb: MiniBIOAsync[E, B])(f: (A, B) => C): MiniBIOAsync[E, C] = {
      suspendSafe {
        val interruptsRef = new AtomicReference[List[InterruptAction[MiniBIOAsync]]](Nil)

        val cleanup = suspendSafe {
          val interrupts = interruptsRef.get()
          interrupts.foldLeft(unit: MiniBIOAsync[Nothing, Unit]) {
            (acc, interrupt) => flatMap(acc)(_ => interrupt.interrupt)
          }
        }

        guarantee(
          f = Async[E, C] {
            (ec, cb) =>
              val (futureA, interruptA) = fa.runOnECInterruptible(ec)
              val (futureB, interruptB) = fb.runOnECInterruptible(ec)
              interruptsRef.set(List(interruptA, interruptB))
              val combined: Future[(Exit.Uninterrupted[E, A], Exit.Uninterrupted[E, B])] =
                futureA.flatMap {
                  exitA =>
                    futureB.map(exitB => (exitA, exitB))(using ec)
                }(using ec)
              combined.onComplete {
                case Success((exitA: Exit.Success[A], exitB: Exit.Success[B])) =>
                  cb(Exit.Success(f(exitA.value, exitB.value)))
                case Success((exitA: Exit.FailureUninterrupted[E], _)) =>
                  cb(exitA)
                case Success((_, exitB: Exit.FailureUninterrupted[E])) =>
                  cb(exitB)
                case Failure(t) =>
                  cb(Exit.Termination.forThrowable(t))
              }(using ec)
          },
          cleanup = cleanup,
        )
      }
    }

    override def parTraverse[E, A, B](l: Iterable[A])(f: A => MiniBIOAsync[E, B]): MiniBIOAsync[E, List[B]] = {
      parTraverseN(Int.MaxValue)(l)(f)
    }

    override def parTraverse_[E, A](l: Iterable[A])(f: A => MiniBIOAsync[E, Unit]): MiniBIOAsync[E, Unit] = {
      parTraverseN_(Int.MaxValue)(l)(f)
    }

    override def parTraverseNCore[E, A, B](l: Iterable[A])(f: A => MiniBIOAsync[E, B]): MiniBIOAsync[E, List[B]] = {
      suspendSafe(parTraverseN(java.lang.Runtime.getRuntime.availableProcessors())(l)(f))
    }

    override def parTraverseNCore_[E, A](l: Iterable[A])(f: A => MiniBIOAsync[E, Unit]): MiniBIOAsync[E, Unit] = {
      suspendSafe(parTraverseN_(java.lang.Runtime.getRuntime.availableProcessors())(l)(f))
    }

    override def parTraverseN[E, A, B](maxParallelism: Int)(l: Iterable[A])(f: A => MiniBIOAsync[E, B]): MiniBIOAsync[E, List[B]] = {
      // from https://github.com/zio/zio/blob/be0fc8a67388dba08c008b76d04197f875eecc9a/core/shared/src/main/scala/zio/ZIO.scala#L6319
      if (l.isEmpty) {
        pure(List.empty)
      } else if (maxParallelism <= 1) {
        traverse(l)(f)
      } else {
        suspendSafe {
          val results = new Array[AnyRef](l.size)
          map(parTraverseN_(maxParallelism)(l.zipWithIndex) {
            case (a, i) =>
              map(f(a)) {
                b =>
                  results(i) = b.asInstanceOf[AnyRef]
              }
          })(_ => results.toList.asInstanceOf[List[B]])
        }
      }
    }

    override def parTraverseN_[E, A](maxParallelism: Int)(l: Iterable[A])(f: A => MiniBIOAsync[E, Unit]): MiniBIOAsync[E, Unit] = {
      // from https://github.com/zio/zio/blob/be0fc8a67388dba08c008b76d04197f875eecc9a/core/shared/src/main/scala/zio/ZIO.scala#L6341
      val realParallelism = math.min(maxParallelism, l.size)
      if (l.isEmpty) {
        unit
      } else if (realParallelism <= 1) {
        traverse_(l)(f)
      } else {
        suspendSafe {
          val interruptsRef = new AtomicReference[List[InterruptAction[MiniBIOAsync]]](Nil)

          val cleanup = suspendSafe {
            val interrupts = interruptsRef.get()
            interrupts.foldLeft(unit: MiniBIOAsync[Nothing, Unit]) {
              (acc, interrupt) => flatMap(acc)(_ => interrupt.interrupt)
            }
          }

          guarantee(
            f = Async[E, Unit] {
              (ec0, cb) =>
                implicit val ec: ExecutionContext = ec0

                import java.util.concurrent.ConcurrentLinkedQueue
                import scala.jdk.CollectionConverters.*

                val queue = new ConcurrentLinkedQueue[A](l.asJavaCollection)
                // NB: parTraverse* must implement short-circuiting - even for an uninterruptible effect,
                // - by analogy with traverse, but this capability is not used in distage-testkit because
                // all tests are sandboxed
                val earlyFailure = new AtomicReference[Option[Exit.FailureUninterrupted[E]]](None)

                val worker: MiniBIOAsync[E, Unit] = {
                  guaranteeOnFailure[E, Unit](
                    f = {
                      def go(): MiniBIOAsync[E, Unit] = suspendSafe {
                        if (earlyFailure.get().isDefined) {
                          unit
                        } else {
                          queue.poll() match {
                            case null => unit
                            case a => flatMap(f(a))(_ => go())
                          }
                        }
                      }
                      go()
                    },
                    cleanupOnFailure = {
                      failure =>
                        val failureUninterrupted = failure match {
                          case uninterrupted: Exit.FailureUninterrupted[E] => uninterrupted
                          case i @ Exit.Interruption(_, _, _) => Exit.Termination.forThrowable(i.toThrowable)
                        }
                        sync(earlyFailure.compareAndSet(None, Some(failureUninterrupted)).discard())
                    },
                  )
                }

                val workerHandles = List.fill(realParallelism)(worker.runOnECInterruptible(ec))
                interruptsRef.set(workerHandles.map(_._2))
                val workerFutures = workerHandles.map(_._1)

                Future
                  .sequence(workerFutures)
                  .onComplete {
                    case Success(exits) =>
                      val mbFailure = earlyFailure.get().orElse(exits.collectFirst(Function.unlift(_.asFailure)))
                      mbFailure match {
                        case Some(failure) => cb(failure)
                        case None => cb(Exit.Success(()))
                      }
                    case Failure(t) =>
                      cb(Exit.Termination(t, Trace.ThrowableTrace(t)))
                  }(using ec)
            },
            cleanup = cleanup,
          )
        }
      }
    }

    // WeakTemporal2
    override def sleep(duration: Duration): MiniBIOAsync[Nothing, Unit] = {
      sleepImpl(duration)
    }
  }

  implicit def UnsafeRunMiniBIOAsync(implicit ec: ExecutionContext): UnsafeRun2[MiniBIOAsync] = new MiniBIOAsyncRunner()(using ec)

  final class MiniBIOAsyncRunner()(implicit ec: ExecutionContext) extends MiniBIOAsyncUnsafeRunPlatformSpecific {

    override def unsafeRunAsync[E, A](io: => MiniBIOAsync[E, A])(callback: Exit[E, A] => Unit): Unit = {
      io.runOnEC(ec).onComplete {
          case scala.util.Success(exit: Exit.Uninterrupted[E, A]) => callback(exit)
          case scala.util.Failure(t) => callback(Exit.Termination(t, Exit.Trace.ThrowableTrace(t)))
        }(using ec)
    }

    override def unsafeRunAsyncAsFuture[E, A](io: => MiniBIOAsync[E, A]): Future[Exit[E, A]] = {
      io.runSyncToFirstAsyncBoundary() match {
        case Left(exit) => Future.successful(exit)
        case Right(continuation) => continuation(ec)
      }
    }

    // MiniBIOAsync doesn't support interruption
    override def unsafeRunAsyncInterruptible[E, A](io: => MiniBIOAsync[E, A])(callback: Exit[E, A] => Unit): InterruptAction[MiniBIOAsync] = {
      val asyncInterrupt = new AsyncInterruptRef
      io.runSyncToFirstAsyncBoundaryInterruptible(asyncInterrupt) match {
        case Left(exit) =>
          callback(exit)
        case Right(continuation) =>
          continuation(ec).onComplete {
            case scala.util.Success(exit: Exit.Uninterrupted[E, A]) => callback(exit)
            case scala.util.Failure(t) => callback(Exit.Termination(t, Exit.Trace.ThrowableTrace(t)))
          }(using ec)
      }
      InterruptAction(MiniBIOAsync.WeakAsyncForMiniBIOAsync.sync(asyncInterrupt.interrupt()))
    }

    override def unsafeRunAsyncAsInterruptibleFuture[E, A](io: => MiniBIOAsync[E, A]): (Future[Exit[E, A]], InterruptAction[MiniBIOAsync]) = {
      val promise = Promise[Exit[E, A]]()
      val interrupt = unsafeRunAsyncInterruptible(io)(exit => promise.success(exit))
      (promise.future, interrupt)
    }
  }

}
