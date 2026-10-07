package izumi.distage.testkit.distagesuite.interruption

import distage.{DefaultModule, Functoid, Identity, TagK}
import izumi.distage.testkit.model.{DistageTest, FullMeta, ScopeId, SuiteId, SuiteMeta, TestConfig, TestId, TestMeta, TestStatus}
import izumi.distage.testkit.runner.api.TestReporter
import izumi.distage.testkit.runner.spec.SpecIdentity
import izumi.distage.testkit.spec.{SessionTestEnvironment, TestEnvironmentFactory}
import izumi.distage.plugins.load.PluginLoaderDefaultImpl
import izumi.distage.plugins.merge.SimplePluginMergeStrategy
import izumi.distage.roles.model.meta.RolesInfo
import izumi.fundamentals.platform.language.SourceFilePositionMaterializer
import izumi.distage.testkit.runner.di.TestRunnerRuntime
import izumi.functional.quasi.QuasiIO.syntax.*
import izumi.functional.quasi.{QuasiIO, QuasiTemporal}
import izumi.fundamentals.platform.language.types.HigherKindedAny.AnyF
import izumi.logstage.api.IzLogger

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future, Promise}
import scala.jdk.CollectionConverters.*
import scala.util.chaining.scalaUtilChainingOps

abstract class InterruptionTest extends SpecIdentity {

  protected def testRunnerRuntime(): TestRunnerRuntime
  private val environments = new SessionTestEnvironment(new TestEnvironmentFactory.Impl)
  private val loader = new PluginLoaderDefaultImpl()

  protected def modifySuites: Seq[InterruptibleTestSuite[AnyF]] => Seq[InterruptibleTestSuite[AnyF]] = identity

  private final val isStressTest = Option(System.getenv("INTERRUPTION_STRESS_TEST")).contains("true")
  private final val parallelRuns = if (isStressTest) 50 else 1
  private final val sequentialRuns = if (isStressTest) 10 else 1
  private final def repeat(n: Int)(f: => Any): Unit = (1 to n).foreach(_ => f)

  "Test runner" should {
    (1 to parallelRuns).foreach {
      n =>
        s"propagate Thread Interrupt signal to all underlying test runtimes, including Identity $n" in repeat(sequentialRuns) {
          implicit val ec: ExecutionContext = ExecutionContext.global
          val testReporter: TestReporter = emptySuiteReporter()

          val allTestsInterrupted = new AtomicBoolean(true)

          def mkSuiteFor[F0[_]: TagK: DefaultModule](id: Int): InterruptibleTestSuite[AnyF] = {
            new InterruptibleTestSuite[F0](
              id = id,
              signalNotInterrupted = () => allTestsInterrupted.set(false),
            ).asInstanceOf[InterruptibleTestSuite[AnyF]]
          }
          def mkSuites[F0[_]: TagK: DefaultModule]: Seq[InterruptibleTestSuite[AnyF]] = {
            (1 to 3).map(mkSuiteFor[F0])
          }

          val suites = modifySuites(mkSuites[Identity] ++ mkSuites[cats.effect.IO] ++ mkSuites[zio.Task])
          val tests: Seq[DistageTest[AnyF]] = suites.flatMap(_.registeredTests())

          val startedTests: Seq[Future[Unit]] = suites.flatMap(_.startedTests.asScala)
          val stoppedTests: Seq[Future[Unit]] = suites.flatMap(_.stoppedTests.asScala)

          // Each nSecondsTest added exactly one started + one stopped promise during suite construction
          assert(startedTests.size == tests.size)
          assert(stoppedTests.size == tests.size)

          val t = new Thread({
            () =>
              val _ = testRunnerRuntime().runTests(testReporter, _ => false, tests).awaitCompletion()
          })
          t.setUncaughtExceptionHandler((_, _) => ())
          t.start()

          Await.result(Future.sequence(startedTests), 30.seconds)

          // Note: on JVM at least one thread MUST block on tests,
          // otherwise there would be no thread available to actually
          // receive the interrupt signal from SBT upon pressing Ctrl-C
          assert(t.isAlive)
          t.interrupt()
          t.join()

          assert(allTestsInterrupted.get())

          Await.result(Future.sequence(stoppedTests), 30.seconds)

          assert(allTestsInterrupted.get())

          ()
        }
    }
  }

  final class InterruptibleTestSuite[F[_]](
    id: Int,
    signalNotInterrupted: () => Unit,
  )(implicit val tagMonoIO: TagK[F],
    val defaultModulesIO: DefaultModule[F],
  ) {

    val startedTests: ConcurrentLinkedQueue[Future[Unit]] = new ConcurrentLinkedQueue[Future[Unit]]()
    val stoppedTests: ConcurrentLinkedQueue[Future[Unit]] = new ConcurrentLinkedQueue[Future[Unit]]()

    private val collected = scala.collection.mutable.ListBuffer.empty[DistageTest[F]]
    def registeredTests(): List[DistageTest[F]] = collected.toList
    private val suiteId = SuiteId(getClass.getName)
    private val suiteMeta = SuiteMeta(suiteId, getClass.getSimpleName.stripSuffix("$"), getClass.getName)
    private val environment = environments.load[F](
      TestConfig.forSuite(getClass), loader,
      RolesInfo(Set.empty, Set.empty, Set.empty, Set.empty, Set.empty, Set.empty),
      SimplePluginMergeStrategy, tagMonoIO, defaultModulesIO,
    )

    private def nSecondsTest(n: Int): Unit = {
        val startedLatch = Promise[Unit]().tap(startedTests `add` _.future)
        val stoppedLatch = Promise[Unit]().tap(stoppedTests `add` _.future)

        val function: Functoid[F[Unit]] = Functoid {
          (FT: QuasiTemporal[F], F0: QuasiIO[F], logger: IzLogger) =>
            implicit val F: QuasiIO[F] = F0
            F.guarantee(for {
              _ <- F.guaranteeOnInterrupt {
                F.suspendF {
                  logger.info(s"\n $n second test started for $id:$tagMonoIO")
                  startedLatch.success(())
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
            } yield ())(F.maybeSuspend(stoppedLatch.success(())))
        }
        val testId = TestId(Seq("when tests are interrupted they", "should", s"be interrupted before $n seconds pass"), suiteId)
        collected += DistageTest(function.asInstanceOf[Functoid[F[Any]]], environment, TestMeta(testId, implicitly[SourceFilePositionMaterializer].get, System.identityHashCode(function).toLong), suiteMeta)
      }

      nSecondsTest(20)
      nSecondsTest(21)
      nSecondsTest(22)
      nSecondsTest(23)
      nSecondsTest(24)

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


}

// another test case - multiple envs cause outer parTraverse to happen. Test with multiple envs?

final class InterruptionTestAsyncMiniBIOAsyncAsync_AllEffects extends InterruptionTest {
  override protected def testRunnerRuntime(): TestRunnerRuntime = TestRunnerRuntime.defaultAsyncRuntime
}

// ZIO

final class InterruptionTestAsyncZIO extends InterruptionTest {
  override protected def testRunnerRuntime(): TestRunnerRuntime = TestRunnerRuntime.defaultAsyncRuntimeFor[zio.Task]
  override protected def modifySuites: Seq[InterruptibleTestSuite[AnyF]] => Seq[InterruptibleTestSuite[AnyF]] = _.filter(_.tagMonoIO == TagK[zio.Task])
}

final class InterruptionTestAsyncZIO_AllEffects extends InterruptionTest {
  override protected def testRunnerRuntime(): TestRunnerRuntime = TestRunnerRuntime.defaultAsyncRuntimeFor[zio.Task]
}

// CIO

final class InterruptionTestAsyncCIO extends InterruptionTest {
  override protected def testRunnerRuntime(): TestRunnerRuntime = TestRunnerRuntime.defaultAsyncRuntimeFor[cats.effect.IO]
  override protected def modifySuites: Seq[InterruptibleTestSuite[AnyF]] => Seq[InterruptibleTestSuite[AnyF]] = _.filter(_.tagMonoIO == TagK[cats.effect.IO])
}

final class InterruptionTestAsyncCIO_AllEffects extends InterruptionTest {
  override protected def testRunnerRuntime(): TestRunnerRuntime = TestRunnerRuntime.defaultAsyncRuntimeFor[cats.effect.IO]
}
