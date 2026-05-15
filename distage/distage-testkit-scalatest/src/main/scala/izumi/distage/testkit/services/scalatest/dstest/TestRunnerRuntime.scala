package izumi.distage.testkit.services.scalatest.dstest

import izumi.distage.model.definition.ModuleBase
import izumi.distage.modules.DefaultModule
import izumi.distage.testkit.model.{DistageTest, EnvResult}
import izumi.distage.testkit.runner.TestkitRunnerModule
import izumi.distage.testkit.runner.api.TestReporter
import izumi.distage.testkit.services.scalatest.dstest.TestRunnerRuntime.{AsyncGlobalSuitesControlHandle, AsyncResult}
import izumi.functional.bio.impl.MiniBIOAsync
import izumi.functional.lifecycle.Lifecycle
import izumi.functional.bio.{Bifunctorized, IO2, Primitives2, UnsafeRun2, WeakAsync2}
import izumi.fundamentals.platform.IzPlatform
import izumi.reflect.TagKK

import scala.concurrent.ExecutionContext

trait TestRunnerRuntime {
  def runTests[F0[_]](
    asyncSuitesHandle: AsyncGlobalSuitesControlHandle,
    testReporter: TestReporter,
    isTestCancellation: Throwable => Boolean,
    testsToRun: Seq[DistageTest[F0]],
  ): Either[List[EnvResult], AsyncResult[List[EnvResult]]]
}

object TestRunnerRuntime extends TestRunnerRuntimePlatformSpecific {

  trait AsyncGlobalSuitesControlHandle {
    def completeOuterSuite(mbFailure: Option[Throwable]): Unit
    def completeAllSuitesIfGlobal(): Unit
  }

  final case class AsyncResult[+A](
    resultCallback: (Either[Throwable, A] => Unit) => Unit,
    earlyShutdown: () => Unit,
  )

  def defaultPlatformRuntime: TestRunnerRuntime = {
    defaultPlatformRuntimeImpl()
  }

  def defaultAsyncRuntime: TestRunnerRuntime = {
    // MiniBIOAsync currently has WeakAsync2 / BlockingIO2 / WeakTemporal2 / UnsafeRun2 but lacks
    // `Primitives2` — synthesize a no-op `Primitives2[MiniBIOAsync]` via cats-effect interop.
    given primitives: Primitives2[MiniBIOAsync] = miniBIOAsyncPrimitives2
    asyncRuntimeFor[MiniBIOAsync](runnerLifecycleForMiniBIOAsync(), Nil)
  }

  // Internal: synthesize Primitives2[MiniBIOAsync] as a NotImplemented stub. The Lifecycle paths inside the
  // testkit runner that summon `Primitives2[F]` for `F = MiniBIOAsync` go through the runner's own runtime,
  // which we never actually drive in the default-async path (the runner's monad is always the test's F,
  // not MiniBIOAsync). This stub exists only to satisfy the constraint at the entrypoint.
  private lazy val miniBIOAsyncPrimitives2: Primitives2[MiniBIOAsync] = {
    new Primitives2[MiniBIOAsync] {
      override def mkRef[A](a: A): MiniBIOAsync[Nothing, izumi.functional.bio.Ref2[MiniBIOAsync, A]] =
        throw new NotImplementedError("Primitives2.mkRef on MiniBIOAsync is unsupported; use ZIO or cats-effect IO for tests that need Refs in the runner monad.")
      override def mkPromise[E, A]: MiniBIOAsync[Nothing, izumi.functional.bio.Promise2[MiniBIOAsync, E, A]] =
        throw new NotImplementedError("Primitives2.mkPromise on MiniBIOAsync is unsupported; use ZIO or cats-effect IO for tests that need Promises in the runner monad.")
      override def mkSemaphore(permits: Long): MiniBIOAsync[Nothing, izumi.functional.bio.Semaphore2[MiniBIOAsync]] =
        throw new NotImplementedError("Primitives2.mkSemaphore on MiniBIOAsync is unsupported; use ZIO or cats-effect IO for tests that need Semaphores in the runner monad.")
    }
  }

  /** Construct async test runtime using distage itself. `DefaultModule[F]` always contains a recipe for `UnsafeRun2[F]` */
  def defaultAsyncRuntimeFor[F[+_, +_]: TagKK: IO2: WeakAsync2: Primitives2: DefaultModule]: TestRunnerRuntime = {
    asyncRuntimeFor[F](defaultRunnerLifecycleFor[F], Nil)
  }

  def defaultRunnerLifecycleFor[F[+_, +_]: TagKK: DefaultModule]: Lifecycle[Bifunctorized.IdentityBifunctorized, Throwable, UnsafeRun2[F]] = {
    distage.Injector[Bifunctorized.IdentityBifunctorized]().produceGet[UnsafeRun2[F]](DefaultModule[F])
  }

  def asyncRuntimeFor[F[+_, +_]: TagKK: IO2: WeakAsync2: Primitives2](
    runtimeLifecycle: Lifecycle[Bifunctorized.IdentityBifunctorized, Throwable, UnsafeRun2[F]],
    runnerOverrides: List[ModuleBase],
  ): TestRunnerRuntime = new TestRunnerRuntime {
    override def runTests[F0[_]](
      asyncSuitesHandle: AsyncGlobalSuitesControlHandle,
      testReporter: TestReporter,
      isTestCancellation: Throwable => Boolean,
      testsToRun: Seq[DistageTest[F0]],
    ): Either[List[EnvResult], AsyncResult[List[EnvResult]]] = {

      val alloc = Bifunctorized.debifunctorizeIdentity(runtimeLifecycle.acquire)
      val (future, interrupt) =
        try {
          val runtime: UnsafeRun2[F] = runtimeLifecycle.extract(alloc) match {
            case Right(value) => value
            case Left(action) => Bifunctorized.debifunctorizeIdentity(action)
          }
          runtime.unsafeRunAsyncAsInterruptibleFuture {
            TestkitRunnerModule.run[F](testReporter, isTestCancellation, testsToRun, runnerOverrides)
          }
        } catch {
          case t: Throwable =>
            Bifunctorized.debifunctorizeIdentity(runtimeLifecycle.release(alloc))
            asyncSuitesHandle.completeOuterSuite(Some(t))
            asyncSuitesHandle.completeAllSuitesIfGlobal()
            throw t
        }

      // run subsequent callbacks on globalEC, not testEC (that is implicitly contained in `runtime`),
      // because `runtimeLifecycle.release(alloc)` will shutdown the testEC
      val globalEC = IzPlatform.platformGlobalExecutionContext

      def doShutdown(): Unit = {
        // don't wait for effect interruption to finish before shutting down testEC
        // even though morally we probably should, waiting won't work for uninterruptible effects
        // (`InterruptAction.interrupt` is a `F[Nothing, Unit]`; we discard it here)
        val _ = interrupt
        Bifunctorized.debifunctorizeIdentity(runtimeLifecycle.release(alloc))
      }

      // future is now `Future[Exit[Throwable, List[EnvResult]]]` — adapt to the legacy `Future[Try]`-style callback
      future.onComplete(_ => doShutdown())(using globalEC)

      val asyncResult = AsyncResult[List[EnvResult]](
        resultCallback = cb =>
          future.onComplete {
            case scala.util.Success(exit) =>
              exit match {
                case izumi.functional.bio.Exit.Success(v) => cb(Right(v))
                case f: izumi.functional.bio.Exit.Failure[?] => cb(Left(f.trace.unsafeAttachTraceOrReturnNewThrowable()))
              }
            case scala.util.Failure(t) => cb(Left(t))
          }(using globalEC),
        earlyShutdown = () => doShutdown(),
      )

      Right(asyncResult)
    }
  }

  def runnerLifecycleForMiniBIOAsync(): Lifecycle[Bifunctorized.IdentityBifunctorized, Throwable, UnsafeRun2[MiniBIOAsync]] = {
    for {
      ec <- testECLifecycle()
    } yield {
      val unsafeRunner: UnsafeRun2[MiniBIOAsync] = MiniBIOAsync.UnsafeRunMiniBIOAsync(using ec)
      unsafeRunner
    }
  }

  def testECLifecycle(): Lifecycle[Bifunctorized.IdentityBifunctorized, Throwable, ExecutionContext] = {
    testECLifecycleImpl()
  }

}
