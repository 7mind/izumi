package izumi.distage.testkit.distagesuite.interruption

import distage.{DefaultModule, Identity, Module, ModuleDef, TagK}
import izumi.distage.testkit.model.{DistageTest, FullMeta, ScopeId, SuiteMeta, TestConfig, TestStatus}
import izumi.distage.testkit.runner.api.TestReporter
import izumi.distage.testkit.runner.impl.RunnerToF
import izumi.distage.testkit.scalatest.Spec1
import izumi.distage.testkit.services.scalatest.dstest.TestRunnerRuntime.AsyncGlobalSuitesControlHandle
import izumi.distage.testkit.services.scalatest.dstest.{ScalatestAbstractDistageSpec, TestRunnerRuntime}
import izumi.functional.bio.impl.MiniBIOAsync
import izumi.functional.quasi.QuasiIO.syntax.*
import izumi.functional.quasi.{QuasiIO, QuasiTemporal}
import izumi.fundamentals.platform.console.TrivialLogger
import izumi.fundamentals.platform.language.types.HigherKindedAny.AnyF
import izumi.logstage.api.IzLogger

import java.util.concurrent.{ConcurrentLinkedQueue, TimeUnit}
import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future, Promise}

abstract class InterruptionTest extends Spec1[Identity] {

  private final val InterruptionStressRepetitions = 50
  private final val multiplier = 10
  def repeat(n: Int)(f: => Any): Unit = (1 to n).foreach(_ => f)

  def modifySuites: Seq[InterruptibleTestSuite[AnyF]] => Seq[InterruptibleTestSuite[AnyF]] = identity
  def modifyInnerModule: Module => Module = identity
  final def asyncRunnerToFOverride[F[_]: TagK]: Module = new ModuleDef {
    make[RunnerToF[F]].from[RunnerToF.AsyncImpl[F]]
  }

  "Test runner" should {
    (1 to InterruptionStressRepetitions).foreach {
      n =>
        s"propagate Thread Interrupt signal to all underlying test runtimes, including Identity $n" in repeat(multiplier) {
          implicit val ec: ExecutionContext = ExecutionContext.global
          val asyncGlobalSuitesControlHandle: AsyncGlobalSuitesControlHandle = emptySuiteControl()
          val testReporter: TestReporter = emptySuiteReporter()

          val allTestsInterrupted = new AtomicBoolean(true)

          // Append-only collectors; each nSecondsTest registers its own unique promises during suite construction
          val startedPromises = new ConcurrentLinkedQueue[Future[Unit]]()
          val stoppedPromises = new ConcurrentLinkedQueue[Future[Unit]]()

          def mkSuites[F0[_]: TagK: DefaultModule]: Seq[InterruptibleTestSuite[AnyF]] = {
            (1 to 3).map(id => mkSuiteFor[F0](id))
          }
          def mkSuiteFor[F0[_]: TagK: DefaultModule](id: Int): InterruptibleTestSuite[AnyF] = {
            new InterruptibleTestSuite[F0](
              id,
              startedPromises = startedPromises,
              stoppedPromises = stoppedPromises,
              signalNotInterrupted = () => allTestsInterrupted.set(false),
            ).asInstanceOf[InterruptibleTestSuite[AnyF]]
          }

          val suites = modifySuites(mkSuites[Identity] ++ mkSuites[cats.effect.IO] ++ mkSuites[zio.Task])
          val tests: Seq[DistageTest[AnyF]] = suites.flatMap(_.registeredTests())

          // Each nSecondsTest added exactly one started + one stopped promise during suite construction
          assert(startedPromises.size() == tests.size, s"started promises ${startedPromises.size()} != tests ${tests.size}")
          assert(stoppedPromises.size() == tests.size, s"stopped promises ${stoppedPromises.size()} != tests ${tests.size}")

          val allStartedFutures: Seq[Future[Unit]] = {
            import scala.jdk.CollectionConverters.*
            startedPromises.asScala.toSeq
          }
          val allStoppedFutures: Seq[Future[Unit]] = {
            import scala.jdk.CollectionConverters.*
            stoppedPromises.asScala.toSeq
          }

          val t = new Thread({
            () =>
              this._doRunTests(TrivialLogger.make[this.type]("abc"), asyncGlobalSuitesControlHandle, testReporter, tests)
          })
          t.setUncaughtExceptionHandler((_, _) => ())
          t.start()

          Await.result(Future.sequence(allStartedFutures), scala.concurrent.duration.Duration(30, TimeUnit.SECONDS))

          // Note: on JVM at least one thread MUST block on tests,
          // otherwise there would be no thread available to actually
          // receive the interrupt signal from SBT upon pressing Ctrl-C
          assert(t.isAlive)
          t.interrupt()
          t.join()

          assert(allTestsInterrupted.get())

          Await.result(Future.sequence(allStoppedFutures), scala.concurrent.duration.Duration(30, TimeUnit.SECONDS))

          assert(allTestsInterrupted.get())

          ()
        }
    }
  }

  final class InterruptibleTestSuite[F[_]](
    id: Int,
    startedPromises: ConcurrentLinkedQueue[Future[Unit]],
    stoppedPromises: ConcurrentLinkedQueue[Future[Unit]],
    signalNotInterrupted: () => Unit,
  )(implicit override val tagMonoIO: TagK[F],
    override val defaultModulesIO: DefaultModule[F],
  ) extends ScalatestAbstractDistageSpec.For1[F] {

    override protected def config: TestConfig = super.config.copy(moduleOverrides = modifyInnerModule(super.config.moduleOverrides))

    "when tests are interrupted they" should {

      def nSecondsTest(n: Int): Unit = {
        // Each test gets its own unique promises, registered into the append-only collectors
        val myStarted = Promise[Unit]()
        val myStopped = Promise[Unit]()
        startedPromises.add(myStarted.future)
        stoppedPromises.add(myStopped.future)

        s"be interrupted before $n seconds pass" in {
          (FT: QuasiTemporal[F], F0: QuasiIO[F], logger: IzLogger) =>
            implicit val F: QuasiIO[F] = F0
            F.guarantee(for {
              _ <- F.guaranteeOnInterrupt {
                F.suspendF {
                  logger.info(s"\n $n second test started for $id:$tagMonoIO")
                  myStarted.success(())
                  FT.sleep(n.seconds)
                }
              } {
                _ =>
                  F.maybeSuspend {
                    logger.info(s"\n $n second test successfully interrupted for $id:$tagMonoIO")
                  }
              }
              _ <- F.maybeSuspend {
                signalNotInterrupted()
                logger.crit(s"\n $n second test was not interrupted for $id:$tagMonoIO")
              }
            } yield ())(F.maybeSuspend(myStopped.success(())))
        }
      }

      nSecondsTest(20)
      nSecondsTest(21)
      nSecondsTest(22)
      nSecondsTest(23)
      nSecondsTest(24)

    }

  }

  private def emptySuiteReporter(): TestReporter = new TestReporter {
    override def beginScope(id: ScopeId): Unit = ()
    override def endScope(id: ScopeId): Unit = ()
    override def beginLevel(scope: ScopeId, depth: Int, suites: List[SuiteMeta]): Unit = ()
    override def endLevel(scope: ScopeId, depth: Int, suites: List[SuiteMeta]): Unit = ()
    override def beginSuite(scopeId: ScopeId, depth: Int, suiteMeta: SuiteMeta): Unit = ()
    override def endSuite(scopeId: ScopeId, depth: Int, suiteMeta: SuiteMeta): Unit = ()
    override def testSetupStatus(scopeId: ScopeId, depth: Int, meta: FullMeta, testStatus: TestStatus.Setup): Unit = ()
    override def testStatus(scope: ScopeId, depth: Int, meta: FullMeta, testStatus: TestStatus): Unit = ()
  }

  private def emptySuiteControl(): AsyncGlobalSuitesControlHandle = new AsyncGlobalSuitesControlHandle {
    override def completeOuterSuite(mbFailure: Option[Throwable]): Unit = ()
    override def completeAllSuitesIfGlobal(): Unit = ()
  }

}

// another test case - multiple envs cause outer parTraverse to happen. Test with multiple envs?

final class InterruptionTestBlockingMiniBIOAsync_AllEffects extends InterruptionTest {
  override protected def testRunnerRuntime(): TestRunnerRuntime = TestRunnerRuntime.defaultBlockingRuntime
}
final class InterruptionTestBlockingMiniBIOAsyncAsyncRunnerToF_AllEffects extends InterruptionTest {
  override protected def testRunnerRuntime(): TestRunnerRuntime = TestRunnerRuntime.blockingRuntimeFor[MiniBIOAsync[Throwable, _]](
    TestRunnerRuntime.runnerLifecycleForMiniBIOAsync(),
    asyncRunnerToFOverride[MiniBIOAsync[Throwable, _]],
  )
}
final class InterruptionTestAsyncMiniBIOAsyncAsync_AllEffects extends InterruptionTest {
  override protected def testRunnerRuntime(): TestRunnerRuntime = TestRunnerRuntime.defaultAsyncRuntime
}
final class InterruptionTestAsyncMiniBIOAsyncAsyncAsyncRunnerToF_AllEffects extends InterruptionTest {
  override protected def testRunnerRuntime(): TestRunnerRuntime = TestRunnerRuntime.asyncRuntimeFor[MiniBIOAsync[Throwable, _]](
    TestRunnerRuntime.runnerLifecycleForMiniBIOAsync(),
    asyncRunnerToFOverride[MiniBIOAsync[Throwable, _]],
  )
}

// ZIO

final class InterruptionTestBlockingZIO extends InterruptionTest {
  override protected def testRunnerRuntime(): TestRunnerRuntime = TestRunnerRuntime.defaultBlockingRuntimeFor[zio.Task]
  override def modifySuites: Seq[InterruptibleTestSuite[AnyF]] => Seq[InterruptibleTestSuite[AnyF]] = _.filter(_.tagMonoIO == TagK[zio.Task])
}
final class InterruptionTestBlockingZIOAsyncRunnerToFF extends InterruptionTest {
  override protected def testRunnerRuntime(): TestRunnerRuntime = TestRunnerRuntime.defaultBlockingRuntimeFor[zio.Task](asyncRunnerToFOverride[zio.Task])
  override def modifySuites: Seq[InterruptibleTestSuite[AnyF]] => Seq[InterruptibleTestSuite[AnyF]] = _.filter(_.tagMonoIO == TagK[zio.Task])
}
final class InterruptionTestAsyncZIO extends InterruptionTest {
  override protected def testRunnerRuntime(): TestRunnerRuntime = TestRunnerRuntime.defaultAsyncRuntimeFor[zio.Task]
  override def modifySuites: Seq[InterruptibleTestSuite[AnyF]] => Seq[InterruptibleTestSuite[AnyF]] = _.filter(_.tagMonoIO == TagK[zio.Task])
}
final class InterruptionTestAsyncZIOAsyncRunnerToF extends InterruptionTest {
  override protected def testRunnerRuntime(): TestRunnerRuntime = TestRunnerRuntime.defaultAsyncRuntimeFor[zio.Task](asyncRunnerToFOverride[zio.Task])
  override def modifySuites: Seq[InterruptibleTestSuite[AnyF]] => Seq[InterruptibleTestSuite[AnyF]] = _.filter(_.tagMonoIO == TagK[zio.Task])
}

final class InterruptionTestBlockingZIO_AllEffects extends InterruptionTest {
  override protected def testRunnerRuntime(): TestRunnerRuntime = TestRunnerRuntime.defaultBlockingRuntimeFor[zio.Task]
}
final class InterruptionTestBlockingZIOAsyncRunnerToF_AllEffects extends InterruptionTest {
  override protected def testRunnerRuntime(): TestRunnerRuntime = TestRunnerRuntime.defaultBlockingRuntimeFor[zio.Task](asyncRunnerToFOverride[zio.Task])
}
final class InterruptionTestAsyncZIO_AllEffects extends InterruptionTest {
  override protected def testRunnerRuntime(): TestRunnerRuntime = TestRunnerRuntime.defaultAsyncRuntimeFor[zio.Task]
}
final class InterruptionTestAsyncZIOAsyncRunnerToF_AllEffects extends InterruptionTest {
  override protected def testRunnerRuntime(): TestRunnerRuntime = TestRunnerRuntime.defaultAsyncRuntimeFor[zio.Task](asyncRunnerToFOverride[zio.Task])
}

// CIO

final class InterruptionTestBlockingCIO extends InterruptionTest {
  override protected def testRunnerRuntime(): TestRunnerRuntime = TestRunnerRuntime.defaultBlockingRuntimeFor[cats.effect.IO]
  override def modifySuites: Seq[InterruptibleTestSuite[AnyF]] => Seq[InterruptibleTestSuite[AnyF]] = _.filter(_.tagMonoIO == TagK[cats.effect.IO])
}
final class InterruptionTestBlockingCIOAsyncRunnerToF extends InterruptionTest {
  override protected def testRunnerRuntime(): TestRunnerRuntime = TestRunnerRuntime.defaultBlockingRuntimeFor[cats.effect.IO](asyncRunnerToFOverride[cats.effect.IO])
  override def modifySuites: Seq[InterruptibleTestSuite[AnyF]] => Seq[InterruptibleTestSuite[AnyF]] = _.filter(_.tagMonoIO == TagK[cats.effect.IO])
}
final class InterruptionTestAsyncCIO extends InterruptionTest {
  override protected def testRunnerRuntime(): TestRunnerRuntime = TestRunnerRuntime.defaultAsyncRuntimeFor[cats.effect.IO]
  override def modifySuites: Seq[InterruptibleTestSuite[AnyF]] => Seq[InterruptibleTestSuite[AnyF]] = _.filter(_.tagMonoIO == TagK[cats.effect.IO])
}
final class InterruptionTestAsyncCIOAsyncRunnerToF extends InterruptionTest {
  override protected def testRunnerRuntime(): TestRunnerRuntime = TestRunnerRuntime.defaultAsyncRuntimeFor[cats.effect.IO](asyncRunnerToFOverride[cats.effect.IO])
  override def modifySuites: Seq[InterruptibleTestSuite[AnyF]] => Seq[InterruptibleTestSuite[AnyF]] = _.filter(_.tagMonoIO == TagK[cats.effect.IO])
}

final class InterruptionTestBlockingCIO_AllEffects extends InterruptionTest {
  override protected def testRunnerRuntime(): TestRunnerRuntime = TestRunnerRuntime.defaultBlockingRuntimeFor[cats.effect.IO]
}
final class InterruptionTestBlockingCIOAsyncRunnerToF_AllEffects extends InterruptionTest {
  override protected def testRunnerRuntime(): TestRunnerRuntime = TestRunnerRuntime.defaultBlockingRuntimeFor[cats.effect.IO](asyncRunnerToFOverride[cats.effect.IO])
}
final class InterruptionTestAsyncCIO_AllEffects extends InterruptionTest {
  override protected def testRunnerRuntime(): TestRunnerRuntime = TestRunnerRuntime.defaultAsyncRuntimeFor[cats.effect.IO]
}
final class InterruptionTestAsyncCIOAsyncRunnerToF_AllEffects extends InterruptionTest {
  override protected def testRunnerRuntime(): TestRunnerRuntime = TestRunnerRuntime.defaultAsyncRuntimeFor[cats.effect.IO](asyncRunnerToFOverride[cats.effect.IO])
}
