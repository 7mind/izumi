package izumi.functional.bio

import izumi.functional.bio.Exit.ZIOExit
import izumi.functional.bio.data.InterruptAction
import zio._izumicompat_.__ZIOSucceedCompat.zioSucceed
import zio.{Executor, Supervisor, Unsafe, ZEnvironment, ZIO, ZLayer}
//import zio.stacktracer.TracingImplicits.disableAutoTrace

import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger}
import scala.concurrent.Future

trait UnsafeRun2[F[_, _]] {
  def unsafeRun[E, A](io: => F[E, A]): A
  def unsafeRunSync[E, A](io: => F[E, A]): Exit[E, A]

  def unsafeRunAsync[E, A](io: => F[E, A])(callback: Exit[E, A] => Unit): Unit
  def unsafeRunAsyncAsFuture[E, A](io: => F[E, A]): Future[Exit[E, A]]

  def unsafeRunAsyncInterruptible[E, A](io: => F[E, A])(callback: Exit[E, A] => Unit): InterruptAction[F]
  def unsafeRunAsyncAsInterruptibleFuture[E, A](io: => F[E, A]): (Future[Exit[E, A]], InterruptAction[F])

  // FIXME: add methods for unsafeRunSyncToFirstBoundaryOrAsyncAsInteruptibleFuture
  // FIXME: better support for sync-only effects - make unsafeRunAsync variants able to return synchronously?
}

object UnsafeRun2 {
  def apply[F[_, _]: UnsafeRun2]: UnsafeRun2[F] = implicitly

  /**
    * @param customCpuPool will replace [[zio.internal.ZScheduler]] if set
    * @param customBlockingPool will replace [[zio.internal.Blocking.blockingExecutor]] if set
    * @param handler will add a Supervisor for fiber failure exits if not Default
    * @param otherRuntimeConfiguration zio.Runtime.* layers can be used to set other configuration options for [[zio.Runtime]]
    * @param initialEnv initial environment
    */
  def createZIO[R](
    customCpuPool: Option[Executor] = None,
    customBlockingPool: Option[Executor] = None,
    handler: FailureHandler = FailureHandler.Default,
    otherRuntimeConfiguration: List[ZLayer[Any, Nothing, Any]] = List.empty,
    initialEnv: ZEnvironment[R] = ZEnvironment.empty,
  ): ZIORunner[R] = {
    new ZIORunner(ZIORunnerSupport.configuration(customCpuPool, customBlockingPool, handler, otherRuntimeConfiguration), initialEnv)
  }

//  def createMonixBIO(s: Scheduler, opts: monix.bio.IO.Options): UnsafeRun2[monix.bio.IO] = new MonixBIORunner(s, opts)

  sealed trait FailureHandler
  object FailureHandler {
    case object Default extends FailureHandler
    final case class Custom(handler: Exit.Failure[Any] => Unit) extends FailureHandler
  }

  class ZIORunner[R](
    val runtimeConfiguration: ZLayer[Any, Nothing, Any], // zio.Runtime.* layers combined with `>+>`
    val initialEnv: ZEnvironment[R],
  ) extends ZIOAsyncRunner[R] {

    override def unsafeRun[E, A](io: => ZIO[R, E, A]): A = {
      unsafeRunSync(io) match {
        case Exit.Success(value) =>
          value

        case failure: Exit.Failure[?] =>
          throw failure.trace.unsafeAttachTraceOrReturnNewThrowable()
      }
    }

    //      ZIOExit.toExit(runtime.unsafe.run(io)(using implicitly, zio.Unsafe))(true) // ZIO's runtime.unsafe.run doesn't work
    override def unsafeRunSync[E, A](io: => ZIO[R, E, A]): Exit[E, A] = {
      val interrupted = new AtomicBoolean(true)
      val result = runtime.unsafe.run {
        ZIOExit.ZIOSignalOnNoExternalInterruptFailure(io)(zioSucceed(interrupted.set(false)))
      }(using implicitly[zio.Trace], Unsafe)
      ZIOExit.toExit(result)(interrupted.get())
    }

  }

  object ZIORunner {

    def failureHandlerSupervisor(handler: FailureHandler.Custom): Supervisor[Unit] = ZIORunnerSupport.failureHandlerSupervisor(handler)
  }

  //  class MonixBIORunner(val s: Scheduler, val opts: monix.bio.IO.Options) extends UnsafeRun2[monix.bio.IO] {
  //    override def unsafeRun[E, A](io: => bio.IO[E, A]): A = {
  //      io.leftMap(TypedError(_)).runSyncUnsafeOpt()(s, opts, implicitly, implicitly)
  //    }
  //    override def unsafeRunSync[E, A](io: => bio.IO[E, A]): Exit[E, A] = {
  //      io.sandboxExit.runSyncUnsafeOpt()(s, opts, implicitly, implicitly)
  //    }
  //    override def unsafeRunAsync[E, A](io: => bio.IO[E, A])(callback: Exit[E, A] => Unit): Unit = {
  //      io.runAsyncOpt(exit => callback(Exit.MonixExit.toExit(exit)))(s, opts); ()
  //    }
  //    override def unsafeRunAsyncAsFuture[E, A](io: => bio.IO[E, A]): Future[Exit[E, A]] = {
  //      val p = scala.concurrent.Promise[Exit[E, A]]()
  //      unsafeRunAsync(io)(p.success)
  //      p.future
  //    }
  //    override def unsafeRunAsyncInterruptible[E, A](io: => bio.IO[E, A])(callback: Exit[E, A] => Unit): InterruptAction[bio.IO] = {
  //      val canceler = io.runAsyncOptF {
  //        case Left(e) => callback(Exit.MonixExit.toExit(e))
  //        case Right(value) => callback(Exit.Success(value))
  //      }(s, opts)
  //      InterruptAction(canceler)
  //    }
  //
  //    override def unsafeRunAsyncAsInterruptibleFuture[E, A](io: => bio.IO[E, A]): (Future[Exit[E, A]], InterruptAction[bio.IO]) = {
  //      val p = scala.concurrent.Promise[Exit[E, A]]()
  //      val canceler = unsafeRunAsyncInterruptible(io)(p.success)
  //      (p.future, canceler)
  //    }
  //  }

  final class NamedThreadFactory(name: String, daemon: Boolean, priority: Option[Int]) extends ThreadFactory {
    private val parentGroup = ThreadPlatform.parentGroup

    private val threadGroup = new ThreadGroup(parentGroup, name)
    private val threadCount = new AtomicInteger(1)
    private val threadHash = Integer.toUnsignedString(this.hashCode())

    override def newThread(r: Runnable): Thread = {
      val newThreadNumber = threadCount.getAndIncrement()

      val thread = new Thread(threadGroup, r)
      thread.setName(s"$name-$newThreadNumber-$threadHash")
      thread.setDaemon(daemon)
      if (priority.isDefined) {
        thread.setPriority(priority.get)
      }
      thread
    }

  }
}
