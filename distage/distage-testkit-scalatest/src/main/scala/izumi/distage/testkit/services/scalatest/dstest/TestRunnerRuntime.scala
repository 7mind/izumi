package izumi.distage.testkit.services.scalatest.dstest

import izumi.distage.model.definition.ModuleBase
import izumi.distage.modules.DefaultModule
import izumi.distage.testkit.model.{DistageTest, EnvResult}
import izumi.distage.testkit.runner.TestkitRunnerModule
import izumi.distage.testkit.runner.api.TestReporter
import izumi.distage.testkit.services.scalatest.dstest.TestRunnerRuntime.{AsyncGlobalSuitesControlHandle, AsyncResult}
import izumi.functional.bio.impl.MiniBIOAsync
import izumi.functional.lifecycle.Lifecycle
import izumi.functional.quasi.{QuasiAsync, QuasiIO, QuasiIORunner}
import izumi.fundamentals.platform.IzPlatform
import izumi.fundamentals.platform.functional.Identity
import izumi.reflect.TagK

import scala.concurrent.{ExecutionContext, Promise}
import scala.util.{Failure, Success}

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
    asyncRuntimeFor[MiniBIOAsync[Throwable, _]](runnerLifecycleForMiniBIOAsync(), Nil)
  }

  /** Construct async test runtime using distage itself. DefaultModule[F] always contains a recipe for `QuasiIORunner[F]` */
  def defaultAsyncRuntimeFor[F[_]: TagK: QuasiIO: QuasiAsync: DefaultModule]: TestRunnerRuntime = {
    asyncRuntimeFor[F](defaultRunnerLifecycleFor[F], Nil)
  }

  def defaultRunnerLifecycleFor[F[_]: TagK: DefaultModule]: Lifecycle[Identity, QuasiIORunner[F]] = {
    distage.Injector[Identity]().produceGet[QuasiIORunner[F]](DefaultModule[F])
  }

  def asyncRuntimeFor[F[_]: TagK: QuasiIO: QuasiAsync](
    runtimeLifecycle: Lifecycle[Identity, QuasiIORunner[F]],
    runnerOverrides: List[ModuleBase],
  ): TestRunnerRuntime = new TestRunnerRuntime {
    override def runTests[F0[_]](
      asyncSuitesHandle: AsyncGlobalSuitesControlHandle,
      testReporter: TestReporter,
      isTestCancellation: Throwable => Boolean,
      testsToRun: Seq[DistageTest[F0]],
    ): Either[List[EnvResult], AsyncResult[List[EnvResult]]] = {

      val alloc = runtimeLifecycle.acquire
      val (future, interrupt) =
        try {
          val runtime = runtimeLifecycle.extract(alloc).merge
          runtime.runFutureInterruptible {
            TestkitRunnerModule.run[F](testReporter, isTestCancellation, testsToRun, runnerOverrides)
          }
        } catch {
          case t: Throwable =>
            runtimeLifecycle.release(alloc)
            asyncSuitesHandle.completeOuterSuite(Some(t))
            asyncSuitesHandle.completeAllSuitesIfGlobal()
            throw t
        }

      // run subsequent callbacks on globalEC, not testEC (that is implicitly contained in `runtime`),
      // because `runtimeLifecycle.release(alloc)` will shutdown the testEC
      val globalEC = IzPlatform.platformGlobalExecutionContext

      val shutdownLock = new Object
      var completed = false
      var cancellation = Option.empty[Promise[Unit]]

      def doShutdown(): Unit = {
        val requested = shutdownLock.synchronized {
          if (completed || cancellation.nonEmpty) None
          else {
            val completion = Promise[Unit]()
            cancellation = Some(completion)
            Some(completion)
          }
        }
        requested.foreach { completion =>
          completion.future.onComplete {
            case Failure(cause) =>
              asyncSuitesHandle.completeOuterSuite(Some(cause))
              asyncSuitesHandle.completeAllSuitesIfGlobal()
            case Success(_) => ()
          }(using globalEC)
          try { val _ = completion.completeWith(interrupt.apply()) }
          catch { case cause: Throwable => val _ = completion.failure(cause) }
        }
      }

      future.onComplete { _ =>
        val pending = shutdownLock.synchronized {
          completed = true
          cancellation.map(_.future)
        }
        pending match {
          case Some(stopping) => stopping.onComplete(_ => runtimeLifecycle.release(alloc))(using globalEC)
          case None => runtimeLifecycle.release(alloc)
        }
      }(using globalEC)

      val asyncResult = AsyncResult[List[EnvResult]](
        resultCallback = cb => future.onComplete(res => cb(res.toEither))(using globalEC),
        earlyShutdown = () => doShutdown(),
      )

      Right(asyncResult)
    }
  }

  def runnerLifecycleForMiniBIOAsync(): Lifecycle[Identity, QuasiIORunner[MiniBIOAsync[Throwable, _]]] = {
    for {
      ec <- testECLifecycle()
    } yield {
      val unsafeRunner = MiniBIOAsync.UnsafeRunMiniBIOAsync(using ec)
      QuasiIORunner.fromBIO[MiniBIOAsync](using unsafeRunner)
    }
  }

  def testECLifecycle(): Lifecycle[Identity, ExecutionContext] = {
    testECLifecycleImpl()
  }

}
