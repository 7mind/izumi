package izumi.functional.bio.impl

import izumi.functional.bio.Exit.Trace
import izumi.functional.bio.data.{InterruptAction, Morphism2, RestoreInterruption2}
import izumi.functional.bio.impl.MiniBIOAsync.Fail
import izumi.functional.bio.{BlockingIO2, Exit, UnsafeRun2, WeakAsync2, WeakTemporal2}
import izumi.fundamentals.collections.nonempty.NEList
import izumi.fundamentals.platform.language.Quirks.Discarder

import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}
import scala.annotation.tailrec
import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success}

/**
  * [[MiniBIO]] extended with support for async operations via the Async constructor.
  *
  * Interruption is cooperative at effect boundaries; running synchronous operations are not interrupted.
  * The direct execution methods project cancellation as Termination; [[UnsafeRun2]] retains Exit.Interruption.
  *
  * Made for use in distage-testkit. Prefer ZIO or cats-bio in production.
  */
sealed trait MiniBIOAsync[+E, +A] {

  /**
    * Runs the effect synchronously until the first async boundary.
    * @return Left if the effect completes synchronously, or Right with a continuation if async execution is needed.
    */
  final def runSyncToFirstAsyncBoundary(): Either[Exit.Uninterrupted[E, A], ExecutionContext => Future[Exit.Uninterrupted[E, A]]] = {
    runSyncToFirstAsyncBoundaryImpl(new MiniBIOAsync.AsyncInterruptRef) match {
      case Left(exit) => Left(MiniBIOAsync.uninterrupted(exit))
      case Right(continuation) => Right(ec => continuation(ec).map(MiniBIOAsync.uninterrupted(_))(using ec))
    }
  }

  private[impl] final def runSyncToFirstAsyncBoundaryInterruptible(
    asyncInterrupt: MiniBIOAsync.AsyncInterruptRef
  ): Either[Exit[E, A], ExecutionContext => Future[Exit[E, A]]] = {
    runSyncToFirstAsyncBoundaryImpl(asyncInterrupt)
  }

  private def runSyncToFirstAsyncBoundaryImpl(
    asyncInterrupt: MiniBIOAsync.AsyncInterrupt
  ): Either[Exit[E, A], ExecutionContext => Future[Exit[E, A]]] = {
    final class Catcher[E0, A0, E1, B](
      val recover: Exit.FailureUninterrupted[E0] => MiniBIOAsync[E1, B],
      f: A0 => MiniBIOAsync[E1, B],
    ) extends (A0 => MiniBIOAsync[E1, B]) {
      override def apply(a: A0): MiniBIOAsync[E1, B] = f(a)
    }

    final class Finalizer[E0, A0, E1, B](
      val recover: Exit.Failure[E0] => MiniBIOAsync[E1, B],
      f: A0 => MiniBIOAsync[E1, B],
    ) extends (A0 => MiniBIOAsync[E1, B]) {
      override def apply(a: A0): MiniBIOAsync[E1, B] = f(a)
    }

    final class RestoreMask(val interruptible: Boolean) extends (Any => MiniBIOAsync[Any, Any]) {
      override def apply(value: Any): MiniBIOAsync[Any, Any] = MiniBIOAsync.RestoreMode(interruptible, MiniBIOAsync.Sync(() => Exit.Success(value)))
      def recover(error: Exit.FailureUninterrupted[Any]): MiniBIOAsync[Any, Any] = MiniBIOAsync.RestoreMode(interruptible, Fail.halt(error))
      def interrupt(error: Exit.Interruption): MiniBIOAsync[Any, Any] = MiniBIOAsync.RestoreMode(interruptible, MiniBIOAsync.Interrupted(error))
    }

    def protectCallback(effect: => MiniBIOAsync[Any, Any]): MiniBIOAsync[Any, Any] = {
      try effect catch { case error: Throwable => Fail.terminate(error) }
    }

    @tailrec def runner(
      op: MiniBIOAsync[Any, Any],
      stack: List[Any => MiniBIOAsync[Any, Any]],
      interruptible: Boolean,
    ): Either[Exit[Any, Any], ExecutionContext => Future[Exit[Any, Any]]] = op match {

      case MiniBIOAsync.FlatMap(io, f) =>
        runner(io, f.asInstanceOf[Any => MiniBIOAsync[Any, Any]] :: stack, interruptible)

      case MiniBIOAsync.Redeem(io, err, succ) =>
        runner(io, new Catcher(err, succ).asInstanceOf[Any => MiniBIOAsync[Any, Any]] :: stack, interruptible)

      case MiniBIOAsync.BracketRedeem(io, err, succ) =>
        runner(io, new Finalizer(err, succ).asInstanceOf[Any => MiniBIOAsync[Any, Any]] :: stack, interruptible)

      case MiniBIOAsync.Mask(body) =>
        val restore = new Morphism2.Instance[MiniBIOAsync, MiniBIOAsync] {
          override def apply[E1, A1](effect: MiniBIOAsync[E1, A1]): MiniBIOAsync[E1, A1] = MiniBIOAsync.InterruptionRegion(interruptible, effect)
        }
        val next = protectCallback(body(restore))
        runner(next, new RestoreMask(interruptible) :: stack, interruptible = false)

      case MiniBIOAsync.InterruptionRegion(mode, io) =>
        runner(io, new RestoreMask(interruptible) :: stack, mode)

      case MiniBIOAsync.RestoreMode(mode, io) =>
        runner(io, stack, mode)

      case MiniBIOAsync.SelfInterrupt =>
        asyncInterrupt.interruptSelf()
        runner(MiniBIOAsync.Sync(() => Exit.Success(())), stack, interruptible)

      case MiniBIOAsync.Sync(_) if interruptible && asyncInterrupt.poll() =>
        runner(MiniBIOAsync.interruption, stack, interruptible)

      case MiniBIOAsync.Sync(a) =>
        val exit =
          try { a() }
          catch {
            case t: Throwable =>
              Exit.Termination(t, Trace.ThrowableTrace(t))
          }
        exit match {
          case Exit.Success(_) if interruptible && asyncInterrupt.poll() =>
            runner(MiniBIOAsync.interruption, stack, interruptible)

          case Exit.Success(value) =>
            stack match {
              case flatMap :: stackRest =>
                runner(protectCallback(flatMap(value)), stackRest, interruptible)

              case Nil =>
                Left(exit)
            }

          case failure: Exit.FailureUninterrupted[?] =>
            runner(Fail.halt(failure), stack, interruptible)

        }

      case MiniBIOAsync.Fail(e) =>
        val err =
          try e()
          catch {
            case t: Throwable =>
              Exit.Termination(t, Trace.ThrowableTrace(t))
          }
        val catcher = stack.dropWhile(frame => !frame.isInstanceOf[Catcher[?, ?, ?, ?]] && !frame.isInstanceOf[Finalizer[?, ?, ?, ?]] && !frame.isInstanceOf[RestoreMask])
        catcher match {
          case (restore: RestoreMask) :: stackRest =>
            runner(restore.recover(err), stackRest, interruptible)

          case (finalizer: Finalizer[?, ?, ?, ?]) :: stackRest =>
            runner(protectCallback(finalizer.asInstanceOf[Finalizer[Any, Any, Any, Any]].recover(err)), stackRest, interruptible)

          case value :: stackRest =>
            runner(protectCallback(value.asInstanceOf[Catcher[Any, Any, Any, Any]].recover(err)), stackRest, interruptible)

          case Nil =>
            Left(err)
        }

      case MiniBIOAsync.Interrupted(error) =>
        val finalizer = stack.dropWhile(frame => !frame.isInstanceOf[Finalizer[?, ?, ?, ?]] && !frame.isInstanceOf[RestoreMask])
        finalizer match {
          case (restore: RestoreMask) :: stackRest =>
            runner(restore.interrupt(error), stackRest, interruptible)
          case value :: stackRest =>
            runner(protectCallback(value.asInstanceOf[Finalizer[Any, Any, Any, Any]].recover(error)), stackRest, interruptible)
          case Nil => Left(error)
        }

      case MiniBIOAsync.Async(register) =>
        runner(MiniBIOAsync.AsyncResult((ec, callback) => register(ec, exit => callback(exit))), stack, interruptible)

      case MiniBIOAsync.AsyncResult(register) =>
        // Hit async boundary - return continuation
        Right {
          (ec: ExecutionContext) =>
            val resultPromise = Promise[Exit[Any, Any]]()
            val resumed = new AtomicBoolean(false)

            def continue(exit: Exit[Any, Any], resumeEc: ExecutionContext): Unit = {
              val nextIO = exit match {
                case success: Exit.Success[?] => MiniBIOAsync.Sync(() => success)
                case failure: Exit.FailureUninterrupted[?] => Fail.halt(failure)
                case interruption: Exit.Interruption => MiniBIOAsync.Interrupted(interruption)
              }
              val next = runnerAsync(nextIO, stack, interruptible, resumeEc)
              next.onComplete(resultPromise.tryComplete)(using resumeEc)
            }

            lazy val interruptAction: () => Unit = () => {
              if (asyncInterrupt.claim(interruptAction, resumed)) {
                ec.execute(() => continue(MiniBIOAsync.interruption.exit, ec))
              }
              ()
            }
            if (interruptible) asyncInterrupt.set(interruptAction)

            def claimResume(): Boolean = {
              val claimed = resumed.compareAndSet(false, true)
              if (claimed) asyncInterrupt.clear(interruptAction)
              claimed
            }

            try {
              val callback = (exit: Exit[Any, Any]) => {
                if (claimResume()) continue(exit, ec)
                ()
              }
              register(ec, callback)
            } catch {
              case t: Throwable =>
                if (claimResume()) continue(Exit.Termination.forThrowable(t), ec)
            }

            resultPromise.future
        }
    }

    def runnerAsync(
      op: MiniBIOAsync[Any, Any],
      stack: List[Any => MiniBIOAsync[Any, Any]],
      interruptible: Boolean,
      ec: ExecutionContext,
    ): Future[Exit[Any, Any]] = {
      runner(op, stack, interruptible) match {
        case Left(earlyResult) => Future.successful(earlyResult)
        case Right(continuation) => continuation(ec)
      }
    }

    runner(this, Nil, interruptible = true).asInstanceOf[Either[Exit[E, A], ExecutionContext => Future[Exit[E, A]]]]
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

  private[impl] final def runOnECInterruptible(ec: ExecutionContext): (Future[Exit[E, A]], InterruptAction[MiniBIOAsync]) = {
    val asyncInterrupt = new MiniBIOAsync.AsyncInterruptRef
    val promise = Promise[Exit[E, A]]()
    ec.execute(
      () => {
        if (asyncInterrupt.poll()) {
          promise.success(MiniBIOAsync.interruption.exit)
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
  private def uninterrupted[E, A](exit: Exit[E, A]): Exit.Uninterrupted[E, A] = exit match {
    case value: Exit.Uninterrupted[E, A] => value
    case Exit.Interruption(error, others, trace) => Exit.Termination(error, NEList(error, others), trace)
  }

  private[impl] sealed trait AsyncInterrupt {
    def set(action: () => Unit): Unit
    def clear(action: () => Unit): Unit
    def claim(action: () => Unit, resumed: AtomicBoolean): Boolean
    def poll(): Boolean
    def interrupt(): Unit
    def interruptSelf(): Unit
  }

  private[impl] final class AsyncInterruptRef extends AsyncInterrupt {
    private var action: Option[() => Unit] = None
    private var externalRequested = false
    private var pending = false
    override def set(action: () => Unit): Unit = {
      val shouldInterrupt = synchronized {
        this.action = Some(action)
        pending
      }
      if (shouldInterrupt) action()
    }
    override def clear(action: () => Unit): Unit = {
      synchronized {
        if (this.action.exists(_ eq action)) this.action = None
      }
    }
    override def claim(action: () => Unit, resumed: AtomicBoolean): Boolean = synchronized {
      if (pending && this.action.exists(_ eq action) && resumed.compareAndSet(false, true)) {
        this.action = None
        pending = false
        true
      } else false
    }
    override def poll(): Boolean = synchronized {
      if (pending) {
        pending = false
        true
      } else false
    }
    override def interrupt(): Unit = {
      val current = synchronized {
        if (!externalRequested) {
          externalRequested = true
          pending = true
        }
        if (pending) action else None
      }
      current.foreach(_.apply())
    }
    override def interruptSelf(): Unit = synchronized { pending = true }
    def isInterrupted: Boolean = synchronized(pending)
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
  private final case class AsyncResult[+E, +A](register: (ExecutionContext, Exit[E, A] => Unit) => Unit) extends MiniBIOAsync[E, A]
  private final case class Mask[+E, +A](body: RestoreInterruption2[MiniBIOAsync] => MiniBIOAsync[E, A]) extends MiniBIOAsync[E, A]
  private final case class InterruptionRegion[+E, +A](interruptible: Boolean, io: MiniBIOAsync[E, A]) extends MiniBIOAsync[E, A]
  private final case class RestoreMode[+E, +A](interruptible: Boolean, io: MiniBIOAsync[E, A]) extends MiniBIOAsync[E, A]
  private case object SelfInterrupt extends MiniBIOAsync[Nothing, Unit]
  private final case class Interrupted(exit: Exit.Interruption) extends MiniBIOAsync[Nothing, Nothing]
  private def interruption: Interrupted = {
    val error = new InterruptedException
    Interrupted(Exit.Interruption(error, Nil, Trace.forThrowable(error)))
  }
  private def haltFailure[E](failure: Exit.Failure[E]): MiniBIOAsync[E, Nothing] = failure match {
    case interruption: Exit.Interruption => Interrupted(interruption)
    case error: Exit.FailureUninterrupted[E] => Fail.halt(error)
  }
  private final case class BracketRedeem[E, A, +E1, +B](
    io: MiniBIOAsync[E, A],
    err: Exit.Failure[E] => MiniBIOAsync[E1, B],
    succ: A => MiniBIOAsync[E1, B],
  ) extends MiniBIOAsync[E1, B]

  implicit object WeakAsyncForMiniBIOAsync extends WeakAsync2[MiniBIOAsync] with BlockingIO2[MiniBIOAsync] with WeakTemporal2[MiniBIOAsync] {
    override def pure[A](a: A): MiniBIOAsync[Nothing, A] = Sync(() => Exit.Success(a))
    override def flatMap[E, A, B](r: MiniBIOAsync[E, A])(f: A => MiniBIOAsync[E, B]): MiniBIOAsync[E, B] = FlatMap(r, f)
    override def fail[E](v: => E): MiniBIOAsync[E, Nothing] = Fail(() => Exit.Error.forTypedError(v))
    override def terminate(v: => Throwable): MiniBIOAsync[Nothing, Nothing] = Fail.terminate(v)
    override def sendInterruptToSelf: MiniBIOAsync[Nothing, Unit] = SelfInterrupt
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

    override def bracketExcept[E, A, B](
      acquire: RestoreInterruption2[MiniBIOAsync] => MiniBIOAsync[E, A]
    )(release: (A, Exit[E, B]) => MiniBIOAsync[Nothing, Unit]
    )(use: A => MiniBIOAsync[E, B]
    ): MiniBIOAsync[E, B] = uninterruptibleExcept { restore =>
      // does not propagate error raised in release if `use` failed, in that case only error from `use` is preserved
      flatMap(acquire(restore))(
        a =>
          BracketRedeem[E, B, E, B](
            io = restore(suspendSafe(use(a))),
            err = e => Redeem[Nothing, Unit, E, Nothing](suspendSafe(release(a, e)), err = _ => haltFailure(e), succ = _ => haltFailure(e)),
            succ = v => map(suspendSafe(release(a, Exit.Success(v))))(_ => v),
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

    override def uninterruptibleExcept[E, A](f: RestoreInterruption2[MiniBIOAsync] => MiniBIOAsync[E, A]): MiniBIOAsync[E, A] = Mask(f)

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
    private sealed trait ParallelChildControl {
      def request: MiniBIOAsync[Nothing, Unit]
      def completion: MiniBIOAsync[Nothing, Unit]
    }

    private final case class ParallelChild[E, A](result: Future[Exit[E, A]], interrupt: InterruptAction[MiniBIOAsync]) extends ParallelChildControl {
      override def request: MiniBIOAsync[Nothing, Unit] = interrupt.interrupt
      override def completion: MiniBIOAsync[Nothing, Unit] = AsyncResult {
        (ec, cb) => result.onComplete {
          case Success(_) => cb(Exit.Success(()))
          case Failure(cause) => cb(Exit.Termination.forThrowable(cause))
        }(using ec)
      }
    }

    private final case class ParallelPair[E, A, B](first: ParallelChild[E, A], second: ParallelChild[E, B])
    private final case class ParallelWorkers[E](children: List[ParallelChild[E, Unit]], firstFailure: AtomicReference[Option[Exit.Failure[E]]])

    private def startChild[E, A](effect: MiniBIOAsync[E, A], ec: ExecutionContext): ParallelChild[E, A] = {
      val (result, interrupt) = effect.runOnECInterruptible(ec)
      ParallelChild(result, interrupt)
    }

    private def stopChildren(children: List[ParallelChildControl]): MiniBIOAsync[Nothing, Unit] = {
      // Signal every child before waiting: one finalizer may depend on another.
      flatMap(traverse_(children)(_.request))(_ => traverse_(children)(_.completion))
    }

    override def zipWithPar[E, A, B, C](fa: MiniBIOAsync[E, A], fb: MiniBIOAsync[E, B])(f: (A, B) => C): MiniBIOAsync[E, C] = {
      bracketCase[E, ParallelPair[E, A, B], C](
        AsyncResult[Nothing, ParallelPair[E, A, B]] {
          (ec, cb) => cb(Exit.Success(ParallelPair(startChild(fa, ec), startChild(fb, ec))))
        }
      )((children, _) => stopChildren(List(children.first, children.second))) {
        children =>
           AsyncResult[E, C] {
            (ec, cb) =>
              
              val combined: Future[(Exit[E, A], Exit[E, B])] =
                children.first.result.flatMap {
                  exitA =>
                    children.second.result.map(exitB => (exitA, exitB))(using ec)
                }(using ec)
              combined.onComplete {
                case Success((Exit.Success(a), Exit.Success(b))) =>
                  cb(Exit.Success(f(a, b)))
                case Success((failure: Exit.Failure[E @unchecked], _)) =>
                  cb(failure)
                case Success((_, failure: Exit.Failure[E @unchecked])) =>
                  cb(failure)
                case Failure(t) =>
                  cb(Exit.Termination.forThrowable(t))
              }(using ec)
                        }
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
        bracketCase[E, ParallelWorkers[E], Unit](
          AsyncResult[Nothing, ParallelWorkers[E]] {
            (ec0, cb) =>
              implicit val ec: ExecutionContext = ec0

              import java.util.concurrent.ConcurrentLinkedQueue
              import scala.jdk.CollectionConverters.*

              val queue = new ConcurrentLinkedQueue[A](l.asJavaCollection)
              // NB: parTraverse* must implement short-circuiting - even for an uninterruptible effect,
              // - by analogy with traverse, but this capability is not used in distage-testkit because
              // all tests are sandboxed
              val earlyFailure = new AtomicReference[Option[Exit.Failure[E]]](None)

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
                      sync(earlyFailure.compareAndSet(None, Some(failure)).discard())
                  },
                )
              }

              val children = List.fill(realParallelism)(startChild(worker, ec))
              cb(Exit.Success(ParallelWorkers(children, earlyFailure)))
          }
        )((workers, _) => stopChildren(workers.children)) {
          workers =>
            AsyncResult[E, Unit] {
              (ec0, cb) =>
                implicit val ec: ExecutionContext = ec0
                Future
                  .sequence(workers.children.map(_.result))
                  .onComplete {
                    case Success(exits) =>
                      val mbFailure = workers.firstFailure.get().orElse(exits.collectFirst(Function.unlift(_.asFailure)))
                      mbFailure match {
                        case Some(failure) => cb(failure)
                        case None => cb(Exit.Success(()))
                      }
                    case Failure(t) =>
                      cb(Exit.Termination(t, Trace.ThrowableTrace(t)))
                  }(using ec)
            }
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
      io.runOnECInterruptible(ec)._1.onComplete {
          case scala.util.Success(exit: Exit[E, A]) => callback(exit)
          case scala.util.Failure(t) => callback(Exit.Termination(t, Exit.Trace.ThrowableTrace(t)))
        }(using ec)
    }

    override def unsafeRunAsyncAsFuture[E, A](io: => MiniBIOAsync[E, A]): Future[Exit[E, A]] = {
      io.runSyncToFirstAsyncBoundaryInterruptible(new AsyncInterruptRef) match {
        case Left(exit) => Future.successful(exit)
        case Right(continuation) => continuation(ec)
      }
    }

    override def unsafeRunAsyncInterruptible[E, A](io: => MiniBIOAsync[E, A])(callback: Exit[E, A] => Unit): InterruptAction[MiniBIOAsync] = {
      val asyncInterrupt = new AsyncInterruptRef
      io.runSyncToFirstAsyncBoundaryInterruptible(asyncInterrupt) match {
        case Left(exit) =>
          callback(exit)
        case Right(continuation) =>
          continuation(ec).onComplete {
            case scala.util.Success(exit: Exit[E, A]) => callback(exit)
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
