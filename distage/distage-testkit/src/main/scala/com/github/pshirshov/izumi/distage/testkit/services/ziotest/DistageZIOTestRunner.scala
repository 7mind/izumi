package com.github.pshirshov.izumi.distage.testkit.services.ziotest

import cats.~>
import com.github.pshirshov.izumi.distage.model.monadic.DIEffectRunner
import com.github.pshirshov.izumi.distage.model.providers.ProviderMagnet
import com.github.pshirshov.izumi.distage.roles.services.{IntegrationChecker, IntegrationCheckerImpl}
import com.github.pshirshov.izumi.distage.testkit.services.dstest.DistageTestRunner.{TestReporter, _}
import com.github.pshirshov.izumi.distage.testkit.services.dstest._
import com.github.pshirshov.izumi.distage.testkit.services.st.adapter.DISyntax0
import com.github.pshirshov.izumi.distage.testkit.services.st.dtest.DistageTestSuiteSyntax
import com.github.pshirshov.izumi.distage.testkit.services.st.dtest.DistageTestSuiteSyntax.ScalatestLikeInWord
import com.github.pshirshov.izumi.fundamentals.platform.jvm.CodePosition
import com.github.pshirshov.izumi.logstage.api.{IzLogger, Log}
import distage._
import zio._
import zio.blocking.{Blocking, blocking}
import zio.test._

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.language.implicitConversions

class DistageZIOTestRunner[F[_]: TagK] extends TestExecutor[String, DistageTest[F]] {

  override def execute(spec: Spec[String, DistageTest[F]], defExec: ExecutionStrategy): UIO[ExecutedSpec[String]] = {
    val tests = spec.fold[Seq[DistageTest[F]]] {
      case Spec.SuiteCase(_, specs, _) => specs.flatten
      case Spec.TestCase(_, test) => Seq(test)
    }

    val distageTestRunner = new DistageTestRunner[F](reporter, checker, runnerEnvironment, tests)

    (for {
      environmentResources <- Task(distageTestRunner.resource())
      _ <- exec(defExec)(environmentResources.map(_._2.toEffect[Task].use {
        case GroupExecutionEnvironmentResource(effectRunner, _, groupContinuationResource) =>
          val zio = toZIOK(effectRunner)
          groupContinuationResource.mapK(zio).map(_.mapK(zio))
            .use(runGroup(distageTestRunner, defExec))
      }.unit))

      fakeResult = FailureDetails.Runtime(Cause.interrupt)
      res = Spec.test("All tests", AssertResult.success(fakeResult))
    } yield res)
      .catchAllCause {
        cause =>
          IO.succeed(Spec.test("All tests", AssertResult.failure(FailureDetails.Runtime(cause))))
      }
  }

  private[this] def runGroup(distageTestRunner: DistageTestRunner[F], defExec: ExecutionStrategy)(g: GroupContinuation[Task]): Task[Unit] = {
    distageTestRunner.proceedGroup(g)(exec(defExec)(_).unit)
  }

  private[this] def exec[E, A](strategy: ExecutionStrategy)(actions: Iterable[IO[E, A]]): IO[E, List[A]] = {
    strategy match {
      case ExecutionStrategy.Sequential => ZIO.collectAll(actions)
      case ExecutionStrategy.Parallel => ZIO.collectAllPar(actions)
      case ExecutionStrategy.ParallelN(n) => ZIO.collectAllParN(n.toLong)(actions)
    }
  }

  private[this] def toZIOK(effectRunner: DIEffectRunner[F]): F ~> Task = {
    Lambda[F ~> Task](toZIO(effectRunner)(_))
  }

  protected def toZIO[A](effectRunner: DIEffectRunner[F])(effect: F[A]): Task[A] = {
    blocking(Task(effectRunner.run(effect))).provide(Blocking.Live)
  }

  protected def reporter: TestReporter = new TestReporter {
    override def beginSuite(id: DistageTestRunner.SuiteData): Unit = ()
    override def endSuite(id: DistageTestRunner.SuiteData): Unit = ()
    override def testStatus(test: DistageTestRunner.TestMeta, testStatus: DistageTestRunner.TestStatus): Unit = ()
  }
  protected def logger: IzLogger = IzLogger(Log.Level.Debug)("phase" -> "test")
  protected def checker: IntegrationChecker = new IntegrationCheckerImpl(logger)
  protected def runnerEnvironment: DistageTestEnvironment[F] = new DistageTestEnvironmentImpl[F]

}


abstract class RunnableDistageZIOTest[F[_]: TagK] {

  lazy val runner: DistageZIOTestRunner[F] = new DistageZIOTestRunner[F]

  protected lazy val logger: IzLogger = IzLogger(Log.Level.Debug)("phase" -> "test")
  protected lazy val tenv = new DistageTestEnvironmentProviderImpl(this.getClass)
  protected lazy val env: TestEnvironment = tenv.loadEnvironment(logger)
  protected lazy val distageSuiteName: String = DistageTestSuiteSyntax.getSimpleNameOfAnObjectsClass(this)
  protected lazy val distageSuiteId: String = this.getClass.getName

  implicit def testSyntax(testName: String): ScalatestLikeInWord[F, Spec[String, DistageTest[F]]] = new ScalatestLikeInWord[F, Spec[String, DistageTest[F]]] {
    override protected def tagMonoIO: TagK[F] = TagK[F]
    override protected def takeIO(function: ProviderMagnet[F[_]], pos: CodePosition): Spec[String, DistageTest[F]] = {
      val id = TestId(
        name = testName,
        suiteName = distageSuiteName,
        suiteId = distageSuiteId,
        suiteClassName = distageSuiteName,
      )
      val distageTest = DistageTest(function, env, TestMeta(id, pos))
      Spec.test(testName, distageTest)
    }
  }

  def tests: Spec[String, DistageTest[F]]

  final def main(args: Array[String]): Unit = {
    val latch = scala.concurrent.Promise[Unit]()
    new RunnableSpec(new TestRunner(runner){})(tests){}
      .unsafeRunAsync {
        _ =>
          println("Tests done")
          latch.success(())
      }
    Await.result(latch.future, Duration.Inf)
  }
}

class ZIODistageZIOTestRunner extends DistageZIOTestRunner[Task] {
  override protected def toZIO[A](effectRunner: DIEffectRunner[Task])(effect: Task[A]): Task[A] = effect
}

abstract class ZIORunnableDistageZIOTest extends RunnableDistageZIOTest[Task] {
  override lazy val runner: DistageZIOTestRunner[Task] = new ZIODistageZIOTestRunner
}
