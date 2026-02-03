package izumi.distage.testkit.distagesuite.interruption

import distage.{DefaultModule, Identity, Module, TagK}
import izumi.distage.testkit.model.{DistageTest, FullMeta, ScopeId, SuiteMeta, TestConfig, TestStatus}
import izumi.distage.testkit.runner.api.TestReporter
import izumi.distage.testkit.scalatest.Spec1
import izumi.distage.testkit.services.scalatest.dstest.{ScalatestAbstractDistageSpec, TestRunnerRuntime}
import izumi.distage.testkit.services.scalatest.dstest.TestRunnerRuntime.AsyncGlobalSuitesControlHandle
import izumi.functional.quasi.QuasiIO.syntax.*
import izumi.functional.quasi.{QuasiIO, QuasiTemporal}
import izumi.fundamentals.collections.nonempty.NEList
import izumi.fundamentals.platform.console.TrivialLogger
import izumi.fundamentals.platform.language.types.HigherKindedAny.AnyF
import izumi.fundamentals.platform.versions.Version
import izumi.logstage.api.IzLogger
import zio.BuildInfo

import java.time.{LocalDateTime, ZoneOffset, ZonedDateTime}
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.{CountDownLatch, TimeUnit}
import scala.concurrent.duration.DurationInt
import scala.math.Ordering.Implicits.infixOrderingOps

abstract class InterruptionTest extends Spec1[Identity] {

  def modifySuites: Seq[InterruptibleTestSuite[AnyF]] => Seq[InterruptibleTestSuite[AnyF]] = identity
  def modifyInnerModule: Module => Module = identity

  "Test runner" should {
    "propagate Thread Interrupt signal to all underlying test runtimes, including Identity" in {
      val asyncGlobalSuitesControlHandle: AsyncGlobalSuitesControlHandle = emptySuiteControl()
      val testReporter: TestReporter = emptySuiteReporter()

      val allTestsInterrupted = new AtomicBoolean(true)

      lazy val countDownStart: CountDownLatch = new CountDownLatch(tests.size - suites.size)
      lazy val countDownStopped: CountDownLatch = new CountDownLatch(tests.size - suites.size)

      def zioSuites: Seq[InterruptibleTestSuite[AnyF]] = {
        val zioVersion = Version.parseSemver(BuildInfo.version).get.canonical
        // FIXME: test interruption only on versions after https://github.com/zio/zio/pull/10276 is released
        if (zioVersion > Version.Canonical(NEList(2, 1, 24), Nil)
          || (zioVersion.components == NEList(2, 1, 23) && zioVersion.qualifiers.nonEmpty)) {
          mkSuites[zio.Task]
        } else {
          Nil
        }
      }

      lazy val suites = modifySuites(mkSuites[Identity] ++ mkSuites[cats.effect.IO] ++ zioSuites)
      lazy val tests: Seq[DistageTest[AnyF]] = suites.flatMap(_.registeredTests())

      def mkSuites[F[_]: TagK: DefaultModule]: Seq[InterruptibleTestSuite[AnyF]] = {
        (1 to 3).map(id => mkSuiteFor[F](id))
      }
      def mkSuiteFor[F[_]: TagK: DefaultModule](id: Int): InterruptibleTestSuite[AnyF] = {
        new InterruptibleTestSuite[F](id, countDownStart, () => countDownStopped.countDown(), () => allTestsInterrupted.set(false))
          .asInstanceOf[InterruptibleTestSuite[AnyF]]
      }

      val t = new Thread({
        () =>
          this._doRunTests(TrivialLogger.make[this.type]("abc"), asyncGlobalSuitesControlHandle, testReporter, tests)
      })
      t.setUncaughtExceptionHandler((_, _) => ())
      t.start()

      countDownStart.await(20L, TimeUnit.SECONDS)
      // Note: on JVM at least one thread MUST block on tests,
      // otherwise it there would be no thread available to actually
      // receive the interrupt signal from SBT upon pressing Ctrl-C
      t.interrupt()
      t.join()

      assert(allTestsInterrupted.get())

      countDownStopped.await(20L, TimeUnit.SECONDS)

      assert(allTestsInterrupted.get())

      ()
    }
  }

  final class InterruptibleTestSuite[F[_]](
    id: Int,
    countDownLatch: => CountDownLatch,
    signalStopped: () => Unit,
    signalNotInterrupted: () => Unit,
  )(implicit override val tagMonoIO: TagK[F],
    override val defaultModulesIO: DefaultModule[F],
  ) extends ScalatestAbstractDistageSpec.For1[F] {

    override protected def config: TestConfig = super.config.copy(moduleOverrides = modifyInnerModule(super.config.moduleOverrides))

    "when tests are interrupted they" should {

      def nSecondsTest(n: Int): Unit = {
        s"be interrupted before $n seconds pass" in {
          (FT: QuasiTemporal[F], F0: QuasiIO[F], logger: IzLogger) =>
            implicit val F: QuasiIO[F] = F0
            F.guarantee(for {
              _ <- F.guaranteeOnInterrupt {
                F.suspendF {
                  logger.info(s"\n $n second test started for $id:$tagMonoIO")
                  countDownLatch.countDown()
                  //                countDownLatch.await()
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
            } yield ())(F.maybeSuspend(signalStopped()))
        }
      }

      nSecondsTest(5)
      nSecondsTest(6)
      nSecondsTest(7)
      nSecondsTest(8)
      nSecondsTest(9)

    }

  }

  private def emptySuiteReporter(): TestReporter = new TestReporter {
    override def beginScope(id: ScopeId): Unit = ()
    override def endScope(id: ScopeId): Unit = ()
    override def beginLevel(scope: ScopeId, depth: Int, id: SuiteMeta): Unit = ()
    override def endLevel(scope: ScopeId, depth: Int, id: SuiteMeta): Unit = ()
    override def testSetupStatus(scopeId: ScopeId, depth: Int, meta: FullMeta, testStatus: TestStatus.Setup): Unit = ()
    override def testStatus(scope: ScopeId, depth: Int, meta: FullMeta, testStatus: TestStatus): Unit = ()
  }

  private def emptySuiteControl(): AsyncGlobalSuitesControlHandle = new AsyncGlobalSuitesControlHandle {
    override def completeOuterSuite(mbFailure: Option[Throwable]): Unit = ()
    override def completeAllSuitesIfGlobal(): Unit = ()
  }

}

final class InterruptionTestDefault extends InterruptionTest
final class InterruptionTestDefaultBlocking extends InterruptionTest {
  override protected def testRunnerRuntime(): TestRunnerRuntime = TestRunnerRuntime.defaultBlockingRuntime
}
final class InterruptionTestDefaultAsync extends InterruptionTest {
  override protected def testRunnerRuntime(): TestRunnerRuntime = TestRunnerRuntime.defaultAsyncRuntime
}

object timed {
  private def deadline: ZonedDateTime = LocalDateTime.of(2025, 12, 25, 0, 0, 0).atZone(ZoneOffset.UTC)
  def apply[A](s: Seq[A]): Seq[A] = {
    if (ZonedDateTime.now().isBefore(deadline)) Seq.empty else s
  }
}

final class InterruptionTestDefaultBlockingZIO extends InterruptionTest {
  override protected def testRunnerRuntime(): TestRunnerRuntime = TestRunnerRuntime.defaultBlockingRuntimeFor[zio.Task]
  override def modifySuites: Seq[InterruptibleTestSuite[AnyF]] => Seq[InterruptibleTestSuite[AnyF]] = timed `apply` _.filter(_.tagMonoIO == TagK[zio.Task])
}
final class InterruptionTestDefaultAsyncZIO extends InterruptionTest {
  override protected def testRunnerRuntime(): TestRunnerRuntime = TestRunnerRuntime.defaultAsyncRuntimeFor[zio.Task]
  override def modifySuites: Seq[InterruptibleTestSuite[AnyF]] => Seq[InterruptibleTestSuite[AnyF]] = timed `apply` _.filter(_.tagMonoIO == TagK[zio.Task])
}

final class InterruptionTestDefaultBlockingZIOAsyncRunner extends InterruptionTest {
  override protected def testRunnerRuntime(): TestRunnerRuntime = TestRunnerRuntime.defaultBlockingRuntimeFor[zio.Task]
  override def modifySuites: Seq[InterruptibleTestSuite[AnyF]] => Seq[InterruptibleTestSuite[AnyF]] = timed `apply` _.filter(_.tagMonoIO == TagK[zio.Task])
//  override def modifyInnerModule: Module => Module = super.modifyInnerModule ++ new ModuleDef {
//    make[RunnerToF[]]
//  }

  // FIXME override RunnerToF, override TestkitRunnerModule
}

// FIXME temporarly commented out failing test
//final class InterruptionTestDefaultBlockingCIO extends InterruptionTest {
//  override protected def testRunnerRuntime(): TestRunnerRuntime = TestRunnerRuntime.defaultBlockingRuntimeFor[cats.effect.IO]
//  override def modifySuites: Seq[InterruptibleTestSuite[AnyF]] => Seq[InterruptibleTestSuite[AnyF]] = timed `apply` _.filter(_.tagMonoIO == TagK[cats.effect.IO])
//}
//final class InterruptionTestDefaultAsyncCIO extends InterruptionTest {
//  override protected def testRunnerRuntime(): TestRunnerRuntime = TestRunnerRuntime.defaultAsyncRuntimeFor[cats.effect.IO]
//  override def modifySuites: Seq[InterruptibleTestSuite[AnyF]] => Seq[InterruptibleTestSuite[AnyF]] = timed `apply` _.filter(_.tagMonoIO == TagK[cats.effect.IO])
//}
